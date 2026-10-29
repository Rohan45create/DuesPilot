package com.duespilot.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supabase Postgres Configuration:
 * - Application pooler: uses DATABASE_URL on transaction pooler (port 6543) with prepareThreshold=0.
 * - Flyway migration: uses DATABASE_URL_DIRECT on session pooler / direct host (port 5432).
 * - HikariCP: maximumPoolSize=5, short connectionTimeout=10000ms, SSL required on remote hosts, startup retry.
 * - Schema isolation: defaultSchema=app, schemas=app,public.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    public static final int HIKARI_MAX_POOL_SIZE = 5;
    public static final long HIKARI_CONNECTION_TIMEOUT_MS = 10000L; // 10s short connection timeout
    public static final long HIKARI_VALIDATION_TIMEOUT_MS = 3000L;
    public static final long HIKARI_IDLE_TIMEOUT_MS = 120000L;       // 2m
    public static final long HIKARI_MAX_LIFETIME_MS = 600000L;       // 10m

    public record DbConnectionInfo(String jdbcUrl, String username, String password) {}

    @Bean
    @Primary
    public DataSource dataSource(Environment env) {
        String rawUrl = resolveDatabaseUrl(env, false);
        String defaultUser = env.getProperty("spring.datasource.username", "duespilot");
        String defaultPass = env.getProperty("spring.datasource.password", "duespilot");

        DbConnectionInfo info = parseConnectionUrl(rawUrl, defaultUser, defaultPass, true);

        HikariConfig config = new HikariConfig();
        config.setPoolName("DuesPilotHikariPool");
        config.setJdbcUrl(info.jdbcUrl());
        if (info.username() != null && !info.username().isBlank()) {
            config.setUsername(info.username());
        }
        if (info.password() != null && !info.password().isBlank()) {
            config.setPassword(info.password());
        }

        config.setMaximumPoolSize(HIKARI_MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT_MS);
        config.setValidationTimeout(HIKARI_VALIDATION_TIMEOUT_MS);
        config.setIdleTimeout(HIKARI_IDLE_TIMEOUT_MS);
        config.setMaxLifetime(HIKARI_MAX_LIFETIME_MS);
        config.setSchema("app");

        // Transaction-mode pooler requires prepareThreshold=0
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("currentSchema", "app,public");

        return createDataSourceWithRetry(config, 5, 2000L);
    }

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(Environment env) {
        String directUrl = resolveDatabaseUrl(env, true);
        String defaultUser = env.getProperty("spring.datasource.username", "duespilot");
        String defaultPass = env.getProperty("spring.datasource.password", "duespilot");

        DbConnectionInfo directInfo = parseConnectionUrl(directUrl, defaultUser, defaultPass, false);

        return configuration -> {
            log.info("Configuring Flyway with direct/session URL: {}", maskCredentials(directInfo.jdbcUrl()));
            configuration
                .dataSource(directInfo.jdbcUrl(), directInfo.username(), directInfo.password())
                .defaultSchema("app")
                .schemas("app", "public")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("4");
        };
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            int maxRetries = 5;
            long delayMs = 2000L;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Executing Flyway migrations on schema app,public (attempt {}/{})", attempt, maxRetries);
                    flyway.migrate();
                    log.info("Flyway migrations executed successfully");
                    return;
                } catch (Exception e) {
                    if (attempt == maxRetries) {
                        log.error("Flyway migration failed after {} attempts", maxRetries, e);
                        throw e;
                    }
                    log.warn("Flyway migration attempt {} failed: {}. Retrying in {} ms...",
                        attempt, e.getMessage(), delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Flyway migration interrupted", ie);
                    }
                }
            }
        };
    }

    public static String resolveDatabaseUrl(Environment env, boolean forDirectFlyway) {
        if (forDirectFlyway) {
            String direct = System.getenv("DATABASE_URL_DIRECT");
            if (direct == null || direct.isBlank()) {
                direct = env.getProperty("DATABASE_URL_DIRECT");
            }
            if (direct != null && !direct.isBlank()) {
                return direct.trim();
            }
        }

        String url = System.getenv("DATABASE_URL");
        if (url == null || url.isBlank()) {
            url = env.getProperty("DATABASE_URL");
        }
        if (url == null || url.isBlank()) {
            url = env.getProperty("spring.datasource.url");
        }
        if (url == null || url.isBlank()) {
            url = env.getProperty("SPRING_DATASOURCE_URL");
        }
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/duespilot";
        }
        return url.trim();
    }

    public static DbConnectionInfo parseConnectionUrl(
            String url,
            String defaultUsername,
            String defaultPassword,
            boolean isAppPooler) {

        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/duespilot";
        }

        String jdbcUrl;
        String username = defaultUsername;
        String password = defaultPassword;

        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            // Parse postgres URI: postgresql://[user[:password]@][netloc][:port][/dbname][?param1=value1&...]
            URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : (isAppPooler ? 6543 : 5432);
            String path = uri.getPath();
            if (path == null || path.isBlank() || path.equals("/")) {
                path = "/postgres";
            }

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                String[] parts = userInfo.split(":", 2);
                username = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (parts.length > 1) {
                    password = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }

            StringBuilder sb = new StringBuilder("jdbc:postgresql://")
                .append(host)
                .append(":")
                .append(port)
                .append(path);

            jdbcUrl = enrichJdbcUrl(sb.toString(), uri.getQuery(), host, isAppPooler);
        } else if (url.startsWith("jdbc:postgresql://")) {
            String host = extractHostFromJdbcUrl(url);
            jdbcUrl = enrichJdbcUrl(url, null, host, isAppPooler);
        } else {
            jdbcUrl = url;
        }

        return new DbConnectionInfo(jdbcUrl, username, password);
    }

    private static String enrichJdbcUrl(String baseJdbcUrl, String extraQuery, String host, boolean isAppPooler) {
        StringBuilder sb = new StringBuilder(baseJdbcUrl);
        boolean hasQuery = sb.indexOf("?") >= 0;

        if (extraQuery != null && !extraQuery.isBlank()) {
            sb.append(hasQuery ? "&" : "?").append(extraQuery);
            hasQuery = true;
        }

        String current = sb.toString();

        if (!current.contains("currentSchema=")) {
            sb.append(hasQuery ? "&" : "?").append("currentSchema=app,public");
            hasQuery = true;
        }

        if (isAppPooler && !current.contains("prepareThreshold=")) {
            sb.append(hasQuery ? "&" : "?").append("prepareThreshold=0");
            hasQuery = true;
        }

        if (isRemoteHost(host) && !current.contains("sslmode=") && !current.contains("ssl=")) {
            sb.append(hasQuery ? "&" : "?").append("sslmode=require");
        }

        return sb.toString();
    }

    private static String extractHostFromJdbcUrl(String jdbcUrl) {
        try {
            Matcher m = Pattern.compile("jdbc:postgresql://([^:/]+)").matcher(jdbcUrl);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return "localhost";
    }

    private static boolean isRemoteHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase();
        return !h.equals("localhost")
            && !h.equals("127.0.0.1")
            && !h.equals("host.docker.internal")
            && !h.equals("postgres");
    }

    private static DataSource createDataSourceWithRetry(HikariConfig config, int maxRetries, long delayMs) {
        HikariDataSource ds = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Initializing HikariCP pool {} (attempt {}/{}) for: {}",
                    config.getPoolName(), attempt, maxRetries, maskCredentials(config.getJdbcUrl()));
                ds = new HikariDataSource(config);

                // Verify initial reachability
                try (Connection conn = ds.getConnection()) {
                    log.info("Database reachable via HikariCP. Connection schema: {}", conn.getSchema());
                }
                return ds;
            } catch (Exception e) {
                if (ds != null && !ds.isClosed()) {
                    try { ds.close(); } catch (Exception ignored) {}
                }
                if (attempt == maxRetries) {
                    log.error("HikariCP initialization failed after {} attempts", maxRetries, e);
                    throw new RuntimeException("Could not initialize database connection pool after " + maxRetries + " attempts", e);
                }
                log.warn("HikariCP connection attempt {}/{} failed ({}), retrying in {} ms...",
                    attempt, maxRetries, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Startup retry interrupted", ie);
                }
            }
        }
        return ds;
    }

    public static String maskCredentials(String url) {
        if (url == null) return "null";
        return url.replaceAll("(?i)(password=)[^&;]+", "$1****")
                  .replaceAll("(?<=://)[^:@/]+:[^@/]+@", "****:****@");
    }
}

package com.duespilot.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duespilot")
public class DuesPilotProperties {

    /**
     * Operation mode: 'replay' (no LLM calls, serves recorded analyses)
     * or 'live' (executes real analyses with caps and limits enforced).
     */
    private String demoMode = "live";

    private RateLimit rateLimit = new RateLimit();
    private Retention retention = new Retention();

    public static class RateLimit {
        private int requestsPerMinute = 60;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }

    public static class Retention {
        private String cron = "0 0 2 * * *";

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }

    public String getDemoMode() {
        return demoMode;
    }

    public void setDemoMode(String demoMode) {
        this.demoMode = demoMode;
    }

    public boolean isReplayMode() {
        return "replay".equalsIgnoreCase(demoMode);
    }

    public boolean isLiveMode() {
        return "live".equalsIgnoreCase(demoMode);
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention;
    }
}

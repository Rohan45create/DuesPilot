package com.duespilot.api;

import com.duespilot.api.config.DuesPilotProperties;
import com.duespilot.api.llm.LlmProperties;
import com.duespilot.api.rate.TavilyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({LlmProperties.class, TavilyProperties.class, DuesPilotProperties.class})
public class DuesPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DuesPilotApplication.class, args);
    }
}

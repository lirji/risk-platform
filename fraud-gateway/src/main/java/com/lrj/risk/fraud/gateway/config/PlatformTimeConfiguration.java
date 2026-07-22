package com.lrj.risk.fraud.gateway.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformTimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}

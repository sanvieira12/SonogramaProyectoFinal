package com.sonograma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class BusinessTimeConfig {

    @Bean
    public Clock businessClock() {
        // Read the current instant independently from the host/JVM default
        // zone. BusinessTime applies the Montevideo zone at the boundary.
        return Clock.systemUTC();
    }
}

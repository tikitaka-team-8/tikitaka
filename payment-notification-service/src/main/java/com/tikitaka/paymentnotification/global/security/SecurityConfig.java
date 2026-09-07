package com.tikitaka.paymentnotification.global.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(InternalServiceKeyProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalServiceKeyProperties internalServiceKeyProperties
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .requestMatchers("/api/v1/payments/**").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(
                        new InternalServiceKeyAuthenticationFilter(internalServiceKeyProperties.key()),
                        AuthorizationFilter.class
                );
        return http.build();
    }
}

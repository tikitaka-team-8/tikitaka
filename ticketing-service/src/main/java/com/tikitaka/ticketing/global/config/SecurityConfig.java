package com.tikitaka.ticketing.global.config;

import com.tikitaka.ticketing.global.security.InternalServiceKeyAuthenticationFilter;
import com.tikitaka.ticketing.global.security.InternalServiceKeyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalServiceKeyProperties internalServiceKeyProperties
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // 내부 서비스 통신 인증 (도메인 공통 필터) - "/api/v1/internal/**" 요청을 컨트롤러 도달 전에 검증
                .addFilterBefore(
                        new InternalServiceKeyAuthenticationFilter(internalServiceKeyProperties.key()),
                        AuthorizationFilter.class
                );

        return http.build();
    }
}

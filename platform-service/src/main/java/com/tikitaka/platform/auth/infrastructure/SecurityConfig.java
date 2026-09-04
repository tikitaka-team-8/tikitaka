package com.tikitaka.platform.auth.infrastructure;

import com.tikitaka.platform.auth.infrastructure.security.GatewayAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { 
        http
                // JWT 기반 Stateless 인증 설정
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(request -> request
                        // Auth API - 비로그인 사용자 접근 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/reissue"
                        ).permitAll()
                        // Event 조회 API - 비로그인 사용자 접근 허용
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/events",
                                "/api/v1/events/*",
                                "/api/v1/events/*/sessions/*"
                        ).permitAll()
                        // 모니터링 엔드포인트 - 인증 없이 접근 허용
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        // 그 외 API - 인증된 사용자만 접근 허용
                        .anyRequest().authenticated()
                )
                // Gateway가 전달한 사용자 정보를 Security Context 인증 객체로 변환
                .addFilterBefore(new GatewayAuthenticationFilter(), AuthorizationFilter.class);

        return http.build();
    }
}

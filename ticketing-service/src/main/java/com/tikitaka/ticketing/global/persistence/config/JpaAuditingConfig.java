package com.tikitaka.ticketing.global.persistence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // TODO: AuditingAware 설정 적용할 시 관련 내용 확장 작성
}

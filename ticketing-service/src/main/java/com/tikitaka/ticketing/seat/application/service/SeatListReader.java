package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 좌석 목록 조회 전용 읽기 컴포넌트 (필드 프로젝션 + 짧은 TTL 캐시).
 *
 * SeatService.getSeatList()에서 이 메서드 호출을 분리해 별도 빈으로 둔 이유는 Spring의
 * @Cacheable이 AOP 프록시를 통해서만 동작하기 때문이다 — 같은 클래스 안에서
 * this.readSeatList(...)처럼 자기 자신을 호출하면 프록시를 거치지 않아 캐싱이 조용히
 * 무시된다. 큐 입장 검증(queueAdmissionValidator.validateAndEnter)은 캐시하면 안 되므로
 * 그대로 SeatService에 남기고, 좌석 조회만 이 빈에 위임한다.
 *
 * 필드 프로젝션(findSeatSummaries)은 S11 부하 테스트로 성능 개선 효과(약 6~8배)를 확인하고
 * 최종 채택한 기본 구현이라 on/off 토글 없이 항상 사용한다. 캐시 on/off·TTL은
 * application.yaml의 seat.list.cache.* 프로퍼티(SeatListCacheConfig 참고)로 제어한다.
 */
@Service
@RequiredArgsConstructor
public class SeatListReader {

    private final ScheduleSeatRepository scheduleSeatRepository;

    @Cacheable(
            cacheNames = "seatList",
            key = "#eventSessionId + ':' + #section + ':' + #grade + ':' + #pageable.pageNumber + ':' + #pageable.pageSize"
    )
    public Page<ScheduleSeatResponse> readSeatList(
            UUID eventSessionId,
            String section,
            String grade,
            Pageable pageable
    ) {
        return scheduleSeatRepository
                .findSeatSummaries(eventSessionId, section, grade, pageable)
                .map(ScheduleSeatResponse::from);
    }
}

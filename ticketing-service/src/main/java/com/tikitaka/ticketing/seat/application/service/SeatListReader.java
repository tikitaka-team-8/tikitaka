package com.tikitaka.ticketing.seat.application.service;

import com.tikitaka.ticketing.seat.domain.entity.ScheduleSeat;
import com.tikitaka.ticketing.seat.domain.repository.ScheduleSeatRepository;
import com.tikitaka.ticketing.seat.presentation.dto.response.ScheduleSeatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 좌석 목록 조회 실험(필드 프로젝션 / 짧은 TTL 캐시) 전용 읽기 컴포넌트.
 *
 * SeatService.getSeatList()에서 이 메서드 호출을 분리해 별도 빈으로 둔 이유는 Spring의
 * @Cacheable이 AOP 프록시를 통해서만 동작하기 때문이다 — 같은 클래스 안에서
 * this.readSeatList(...)처럼 자기 자신을 호출하면 프록시를 거치지 않아 캐싱이 조용히
 * 무시된다. 큐 입장 검증(queueAdmissionValidator.validateAndEnter)은 캐시하면 안 되므로
 * 그대로 SeatService에 남기고, 좌석 조회만 이 빈에 위임한다.
 *
 * 외부 설정(application.yaml/@ConfigurationProperties)을 쓰지 않고, 실험용 토글을 아래
 * 상수로 코드에 직접 박아뒀다. 값을 바꾼 뒤 재빌드·재기동해야 반영된다.
 */
@Service
@RequiredArgsConstructor
public class SeatListReader {

    // "필요한 필드만 SELECT" 실험용 토글. true면 ScheduleSeat 엔티티 전체 대신
    // 응답에 필요한 7개 컬럼만 JPQL 생성자 표현식(findSeatSummaries)으로 조회한다.
    private static final boolean PROJECTION_ENABLED = true;

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
        if (PROJECTION_ENABLED) {
            return scheduleSeatRepository
                    .findSeatSummaries(eventSessionId, section, grade, pageable)
                    .map(ScheduleSeatResponse::from);
        }

        Page<ScheduleSeat> seats = scheduleSeatRepository.findSeats(eventSessionId, section, grade, pageable);
        return seats.map(ScheduleSeatResponse::from);
    }
}

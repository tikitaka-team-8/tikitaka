package com.tikitaka.platform.event.infrastructure;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.application.query.PublicEventSummaryResult;
import com.tikitaka.platform.event.domain.EventStatus;
import com.tikitaka.platform.event.domain.QEvent;
import com.tikitaka.platform.venue.domain.QVenue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class EventRepositoryCustomImpl implements EventRepositoryCustom {


  // DRAFT와 CANCELED 상태는 제외
  private static final List<EventStatus> PUBLIC_STATUS = List.of(
      EventStatus.UPCOMING,
      EventStatus.ON_SALE,
      EventStatus.SALE_CLOSED,
      EventStatus.COMPLETED
  );

  private final JPAQueryFactory queryFactory;

  private final QEvent event = QEvent.event;
  private final QVenue venue = QVenue.venue;

  @Override
  public Page<PublicEventSummaryResult> findPublicEvents(
      PublicEventSearchCondition condition,
      Pageable pageable
  ) {

    List<PublicEventSummaryResult> content = queryFactory
        .select(Projections.constructor(
            PublicEventSummaryResult.class,
            event.id,
            event.title,
            venue.name,
            event.status
        ))
        .from(event)
        .join(event.venue, venue)
        .where(
            event.status.in(PUBLIC_STATUS),
            titleContains(condition.keyword()),
            venueIdEq(condition.venueId())
        )
        .orderBy(
            event.title.asc(),
            event.id.asc()
        )
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    // 전체 공연 개수 조회
    JPAQuery<Long> countQuery = queryFactory
        .select(event.count())
        .from(event)
        .where(
            event.status.in(PUBLIC_STATUS),
            titleContains(condition.keyword()),
            venueIdEq(condition.venueId())
        );

    return PageableExecutionUtils.getPage(
        content,
        pageable,
        countQuery::fetchOne
    );
  }

  // keyword 제목 부분 검색
  private BooleanExpression titleContains(String keyword) {
    return StringUtils.hasText(keyword)
        ? event.title.containsIgnoreCase(keyword)
        : null;
  }

  // venueId 공연장 조건
  private BooleanExpression venueIdEq(UUID venueId) {
    return venueId != null
        ? event.venue.id.eq(venueId)
        : null;
  }
}

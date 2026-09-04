package com.tikitaka.platform.event.infrastructure;

import com.tikitaka.platform.event.application.query.PublicEventSearchCondition;
import com.tikitaka.platform.event.application.query.PublicEventSummaryResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventRepositoryCustom {

  Page<PublicEventSummaryResult> findPublicEvents(
      PublicEventSearchCondition condition,
      Pageable pageable
  );
}

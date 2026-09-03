package com.tikitaka.platform.global.response;

import org.springframework.data.domain.Page;

public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

  public static PageMeta from(Page<?> page) {
    return new PageMeta(
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext()
    );
  }
}

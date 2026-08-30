package com.tikitaka.paymentnotification.global.response;

public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}

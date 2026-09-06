# 트러블슈팅 기간 검토 목록

MVP 구현 중 확인했지만 현재 핵심 흐름을 완성하기 위해 후속 기간으로 미룬 안정성·복구·정합성 개선 사항을 기록한다.

## Reservation

### 예매 저장 후 Payment 생성 전 처리가 중단된 경우

- 발견 배경: `Reservation(PAYMENT_PENDING)` 저장과 SeatHold 연장 후 Payment 생성 호출 전에 서버 장애가 발생할 수 있음
- MVP 범위: 정상적인 예매 생성과 동일 멱등 요청 반환 흐름을 우선 구현
- 발생 가능한 문제: 동일 멱등키 재요청 시 `paymentId`가 없는 기존 예매만 반환되어 결제 생성이 이어지지 않을 수 있음
- 추후 검토: `PAYMENT_PENDING + paymentId 없음` 상태에서 Payment 생성을 이어서 처리할지, 별도 복구 작업으로 처리할지 결정

### 외부 Payment 호출과 로컬 DB 상태의 정합성

- 발견 배경: Payment 생성 성공과 Reservation 상태 저장은 하나의 DB 트랜잭션으로 묶을 수 없음
- MVP 범위: Payment 생성 결과에 따라 `PAYMENT_PROCESSING` 또는 `FAILED`로 전환하는 기본 흐름 구현
- 발생 가능한 문제: Payment는 생성됐지만 Ticketing 상태 저장이 실패하거나 응답이 유실될 수 있음
- 추후 검토: Payment 조회·재시도, 단계별 트랜잭션 분리, 보상 처리 및 타임아웃 복구 정책 보강

### Platform 내부 API 연동 공통화 및 오류 처리 고도화

- 발견 배경: MVP에서는 Reservation용 Feign Client가 `X-Service-Key`를 직접 전달하고, timeout과 그 밖의 Feign 실패만 공통 오류로 구분함
- MVP 범위: `RetryableException`은 `DOWNSTREAM_SERVICE_TIMEOUT`, 나머지 Feign 오류는 `DOWNSTREAM_SERVICE_FAILURE`로 변환
- 발생 가능한 문제: 연결 실패와 실제 응답 timeout의 세부 구분이 부족하고, 내부 Client가 늘어나면 헤더 전달 및 예외 변환 코드가 중복될 수 있음
- 추후 검토: 공통 RequestInterceptor·ErrorDecoder, 하위 서비스 `ErrorResponse` 전달 정책, 재시도·서킷 브레이커 도입 여부 검토

### 다중 SeatHold 연장의 일괄 처리

- 발견 배경: 현재는 단일 좌석 선점을 전제로 `validateAndExtend()`를 SeatHold별로 호출함
- MVP 범위: 단일 SeatHold의 상태와 만료 시각을 검증하고 한 번만 연장
- 발생 가능한 문제: 다중 선점 도입 시 반복 조회와 잠금 순서에 따라 처리 비용이나 동시성 충돌이 증가할 수 있음
- 추후 검토: 모든 SeatHold를 먼저 일괄 검증한 뒤 전부 연장하거나 전부 실패하도록 하는 배치 처리와 잠금 순서 결정

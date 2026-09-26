# TIKITAKA API 명세서

현재 Controller, DTO, Bean Validation과 예외 처리 코드 기준의 외부 REST API 명세입니다.

## 1. 공통 규칙

### 접속 정보

| 항목 | 값 |
| --- | --- |
| 외부 API 진입점 | Gateway `http://localhost:8000` |
| Swagger UI | `http://localhost:8000/swagger-ui/index.html` |
| Platform JSON | `/openapi/platform` |
| Ticketing JSON | `/openapi/ticketing` |
| Payment & Notification JSON | `/openapi/payment-notification` |
| Swagger 활성 환경 | `local`, `docker` |
| Swagger 비활성 환경 | `test`, `staging`, 기본 프로파일 |
| 내부 API | `/api/v1/internal/**` — 외부 접근 및 공개 명세 제외 |

### 인증과 헤더

| 헤더 | 입력 주체 | 용도 |
| --- | --- | --- |
| `Authorization: Bearer <token>` | 클라이언트 | 보호 API 인증 |
| `X-Queue-Token` | 클라이언트 | 대기열 입장 권한 |
| `Idempotency-Key` | 클라이언트 | 좌석 선점·예매 중복 방지 |
| `X-User-Id` | Gateway | JWT 사용자 ID 전달 |
| `X-User-Role` | Gateway | JWT 역할 전달 |
| `X-Service-Key` | 내부 서비스 | 내부 API 인증 |

클라이언트가 보낸 `X-User-Id`, `X-User-Role`, `X-Service-Key`는 Gateway가 제거합니다. JWT 역할은 `USER`, `ORGANIZER`, `ADMIN`입니다.

### 공통 성공 응답

```json
{
  "timestamp": "2026-09-22T01:00:00Z",
  "traceId": "00000000-0000-0000-0000-000000000000",
  "code": "SUCCESS",
  "status": 200,
  "message": "요청 처리 결과",
  "data": {}
}
```

| 필드 | 타입 | 조건 |
| --- | --- | --- |
| `timestamp` | string | 필수, ISO 8601 UTC |
| `traceId` | UUID | 필수, 응답 `X-Trace-Id`와 동일 |
| `code` | string | 필수, 성공 시 `SUCCESS` |
| `status` | integer | 필수, HTTP 상태와 동일 |
| `message` | string | 필수 |
| `data` | object/array | 반환 데이터가 있을 때 |
| `meta` | object | 페이징 응답일 때 |

```json
{
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  }
}
```

`204 No Content`에는 본문이 없습니다.

### 공통 오류 응답

```json
{
  "timestamp": "2026-09-22T01:00:00Z",
  "traceId": "00000000-0000-0000-0000-000000000000",
  "code": "C-002",
  "status": 400,
  "message": "입력값이 올바르지 않습니다.",
  "errors": {
    "fieldName": "필드 오류 메시지",
    "_global": "요청 전체 오류 메시지"
  }
}
```

| 코드 | HTTP | 의미 |
| --- | ---: | --- |
| `C-001` | 400 | 지원하지 않는 요청 |
| `C-002` | 400 | 입력값 검증 실패 |
| `C-003` | 502 | 하위 서비스 호출 실패 |
| `C-004` | 504 | 하위 서비스 응답 시간 초과 |
| `C-005` | 500 | 예상하지 못한 서버 오류 |
| `C-006` | 503 | 일시적인 서비스 이용 불가 |

도메인 오류 접두사: `A` Auth, `U` User, `O` Organizer, `E` Event, `V` Venue, `Q` Queue, `S` Seat, `R` Reservation, `P` Payment, `N` Notification.

---

## 2. 전체 API 명세

### Platform Service

| 도메인 | 메서드 | 경로 | 인증 | 성공 | 설명 |
| --- | --- | --- | --- | ---: | --- |
| Auth | POST | `/api/v1/auth/signup` | X | 201 | 회원가입 |
| Auth | POST | `/api/v1/auth/login` | X | 200 | 로그인 |
| Auth | POST | `/api/v1/auth/reissue` | X | 200 | 토큰 재발급 |
| Auth | POST | `/api/v1/auth/logout` | O | 204 | 로그아웃 |
| User | GET | `/api/v1/users/me` | O | 200 | 내 정보 조회 |
| User | PATCH | `/api/v1/users/me` | O | 200 | 내 정보 수정 |
| User | PUT | `/api/v1/users/me/password` | O | 204 | 비밀번호 변경 |
| Organizer | POST | `/api/v1/organizers` | O | 201 | 주최자 등록 |
| Organizer | GET | `/api/v1/organizers/me` | O | 200 | 내 주최자 조회 |
| Organizer | PATCH | `/api/v1/organizers/me` | O | 200 | 내 주최자 수정 |
| Event | GET | `/api/v1/events` | X | 200 | 공개 공연 목록 |
| Event | GET | `/api/v1/events/{eventId}` | X | 200 | 공개 공연 상세 |
| Event | GET | `/api/v1/events/{eventId}/sessions/{sessionId}` | X | 200 | 공개 회차 상세 |
| Event | POST | `/api/v1/organizers/me/events` | O | 201 | 공연 등록 |
| Event | PATCH | `/api/v1/organizers/me/events/{eventId}/status` | O | 200 | 공연 상태 변경 |
| Session | POST | `/api/v1/organizers/me/events/{eventId}/sessions` | O | 201 | 회차 등록 |
| Price | PUT | `/api/v1/organizers/me/events/{eventId}/sessions/{sessionId}/section-prices` | O | 200 | 구역별 가격 설정 |
| Price | GET | `/api/v1/organizers/me/events/{eventId}/sessions/{sessionId}/section-prices` | O | 200 | 구역별 가격 조회 |

### Ticketing Service

| 도메인 | 메서드 | 경로 | 인증 | 인증 외 필수 헤더 | 성공 | 설명 |
| --- | --- | --- | --- | --- | ---: | --- |
| Queue | POST | `/api/v1/event-sessions/{sessionId}/queue` | O | - | 200 | 대기열 진입 |
| Queue | GET | `/api/v1/event-sessions/{sessionId}/queue/me` | O | - | 200 | 내 대기열 조회 |
| Queue | DELETE | `/api/v1/event-sessions/{sessionId}/queue/me` | O | - | 200 | 대기열 이탈 |
| Queue | POST | `/api/v1/event-sessions/{sessionId}/queue/me/heartbeat` | O | - | 204 | heartbeat 갱신 |
| Seat | GET | `/api/v1/schedules/{eventSessionId}/seats` | O | `X-Queue-Token` | 200 | 좌석 목록 |
| Seat | GET | `/api/v1/schedules/{eventSessionId}/seats/{scheduleSeatId}` | O | - | 200 | 좌석 상세 |
| Seat | POST | `/api/v1/schedules/{eventSessionId}/seats/{scheduleSeatId}/hold` | O | `Idempotency-Key` | 201 | 좌석 선점 |
| Seat | DELETE | `/api/v1/schedules/seat-holds/{seatHoldId}` | O | - | 204 | 선점 취소 |
| Reservation | POST | `/api/v1/reservations` | O | `Idempotency-Key` | 201/200 | 예매 생성·멱등 재조회 |
| Reservation | GET | `/api/v1/reservations` | O | - | 200 | 예매 목록 |
| Reservation | GET | `/api/v1/reservations/{reservationId}` | O | - | 200 | 예매 상세 |

Ticketing의 모든 공개 API는 Bearer 인증이 필요합니다.

### Payment & Notification Service

| 도메인 | 메서드 | 경로 | 인증 | 성공 | 설명 |
| --- | --- | --- | --- | ---: | --- |
| Payment | GET | `/api/v1/payments/{paymentId}` | O | 200 | 결제 상세 |
| Payment | POST | `/api/v1/payments/{paymentId}/approve` | O | 200 | 결제 승인 |
| Notification | GET | `/api/v1/notifications` | O | 200 | 알림 목록 |
| Notification | PATCH | `/api/v1/notifications/{notificationId}/read` | O | 200 | 알림 읽음 처리 |

---

## 3. 요청·응답 상세

### Auth

#### 회원가입

```json
{
  "email": "user@example.com",
  "password": "Password1!",
  "name": "사용자",
  "nickname": "nickname",
  "phone": "010-0000-0000"
}
```

| 필드 | 필수 | 검증 |
| --- | --- | --- |
| `email` | O | 이메일, 최대 255자, trim 후 소문자 |
| `password` | O | 8~64자, 영문·숫자·특수문자 포함, 공백 금지 |
| `name` | O | 최대 50자 |
| `nickname` | O | 최대 50자 |
| `phone` | X | 최대 20자, 빈 문자열은 `null` |

```json
{
  "userId": 1,
  "email": "user@example.com",
  "nickname": "nickname",
  "role": "USER"
}
```

#### 로그인·재발급

```json
{ "email": "user@example.com", "password": "Password1!" }
```

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

재발급과 로그아웃 요청은 `{"refreshToken":"<refresh-token>"}`입니다.

### User

| API | 요청 필드 | 검증 | 응답 `data` |
| --- | --- | --- | --- |
| 내 정보 조회 | 없음 | — | `userId`, `email`, `name`, `nickname`, `phone`, `role`, `status`, `createdAt` |
| 내 정보 수정 | `name`, `nickname`, `phone` 선택 | 이름·닉네임 1~50자, phone 최대 20자, 빈 객체 불가 | `userId`, `name`, `nickname`, `phone`, `updatedAt` |
| 비밀번호 변경 | `currentPassword`, `newPassword` 필수 | 새 비밀번호 8~64자 및 조합 규칙 | 본문 없음 |

```json
{
  "name": "수정 이름",
  "nickname": "newNickname",
  "phone": null
}
```

### Organizer

| 필드 | 등록 | 수정 | 검증 |
| --- | --- | --- | --- |
| `name` | O | X | 최대 100자 |
| `representativeName` | O | X | 최대 100자 |
| `contactEmail` | O | X | 이메일, 최대 100자 |
| `contactPhone` | X | X | 8~20자 |
| `description` | X | X | 최대 1,000자 |

응답: `organizerId`, 위 필드, `status`, `approvedAt` 
상태: `PENDING`, `ACTIVE`, `SUSPENDED`, `REJECTED`

### Event와 Session

#### 공개 공연 목록 Query

| 파라미터 | 필수 | 기본값/검증 |
| --- | --- | --- |
| `keyword` | X | 최대 200자 |
| `venueId` | X | UUID |
| `page` | X | 0, 최소 0 |
| `size` | X | 20, 1~100 |

```json
[
  {
    "eventId": "00000000-0000-0000-0000-000000000000",
    "title": "공연 제목",
    "venueName": "공연장",
    "status": "ON_SALE"
  }
]
```

공개 상태: `UPCOMING`, `ON_SALE`, `SALE_CLOSED`, `COMPLETED`

#### 공연 등록

```json
{
  "venueId": "00000000-0000-0000-0000-000000000000",
  "title": "공연 제목",
  "description": "공연 설명",
  "runningTimeMinutes": 120
}
```

| 필드 | 필수 | 검증 |
| --- | --- | --- |
| `venueId` | O | UUID |
| `title` | O | 최대 200자 |
| `description` | X | 별도 길이 제약 없음 |
| `runningTimeMinutes` | O | 1 이상 |

상태 변경 요청: `{"targetStatus":"UPCOMING"}`. 허용 값은 `UPCOMING`, `CANCELED`

#### 회차 등록

```json
{
  "performanceStartAt": "2026-10-01T19:00:00+09:00",
  "performanceEndAt": "2026-10-01T21:00:00+09:00",
  "salesOpenAt": "2026-09-01T10:00:00+09:00",
  "salesCloseAt": "2026-10-01T18:00:00+09:00",
  "queueEnabled": true
}
```

모든 필드가 필수입니다. 
응답: `sessionId`, `sessionNumber`, `queueEnabled`, `status`

#### 회차 가격 설정

```json
{
  "sectionPrices": [
    {
      "venueSectionId": "00000000-0000-0000-0000-000000000000",
      "seatGrade": "R",
      "priceAmount": 100000,
      "salesEnabled": true
    }
  ]
}
```

`sectionPrices`는 비어 있을 수 없습니다. `seatGrade`는 최대 30자, `priceAmount`는 0 이상입니다.

### Queue

```json
{
  "sessionId": "00000000-0000-0000-0000-000000000000",
  "userId": 1,
  "status": "WAITING",
  "position": 10,
  "joinedAt": "2026-09-22T01:00:00Z",
  "admittedAt": null,
  "expiresAt": null,
  "admissionToken": null
}
```

상태: `WAITING`, `ADMITTED`, `ENTERED`, `EXPIRED`. 이탈 응답은 `{"sessionId":"<UUID>","queueStatus":"LEFT"}`.

### Seat와 Seat Hold

좌석 목록 Query:

| 파라미터 | 필수 | 기본값/검증 |
| --- | --- | --- |
| `section` | X | 구역 필터 |
| `grade` | X | 좌석 등급 필터 |
| `page` | X | 0 |
| `size` | X | 50 |

응답의 `data`에는 좌석 목록이, `meta`에는 페이지 정보가 포함됩니다.

```json
{
  "scheduleSeatId": "00000000-0000-0000-0000-000000000000",
  "section": "A",
  "rowLabel": "1",
  "seatNumber": "1",
  "seatGrade": "R",
  "price": 100000,
  "seatStatus": "AVAILABLE"
}
```

좌석 상태: `AVAILABLE`, `HELD`, `SOLD`, `EXCLUDED`

```json
{
  "seatHoldId": "00000000-0000-0000-0000-000000000000",
  "holdToken": "00000000-0000-0000-0000-000000000000",
  "expiresAt": "2026-09-22T01:05:00Z"
}
```

### Reservation

#### 예매 생성

```json
{
  "seatHoldIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```

`seatHoldIds`는 비어 있을 수 없습니다.

```json
{
  "reservationId": "00000000-0000-0000-0000-000000000000",
  "reservationNumber": "reservation-number",
  "paymentId": "00000000-0000-0000-0000-000000000000",
  "reservationStatus": "PAYMENT_PENDING",
  "seatCount": 1,
  "totalAmount": 100000,
  "createdAt": "2026-09-22T01:00:00Z"
}
```

#### 예매 목록 Query

| 파라미터 | 필수 | 기본값/검증 |
| --- | --- | --- |
| `eventTitle` | X | 최대 200자 |
| `reservationStatus` | X | 상태 값 |
| `page` | X | 0 |
| `size` | X | 10 |
| `sort` | X | `createdAt,DESC` |

`eventTitle`과 `reservationStatus`는 동시에 사용할 수 없습니다. 
상태: `PAYMENT_PENDING`, `PAYMENT_PROCESSING`, `CONFIRMED`, `FAILED`, `CANCEL_PENDING`, `CANCELLED`

상세 응답은 목록 필드에 `failureReason`, `paymentCompletedAt`, `seats[]`가 추가됩니다. 
실패 사유: `SEAT_HOLD_EXPIRED`, `PAYMENT_TIMEOUT`, `PAYMENT_FAILED`

### Payment

```json
{
  "paymentId": "00000000-0000-0000-0000-000000000000",
  "reservationId": "00000000-0000-0000-0000-000000000000",
  "orderId": "order-id",
  "amount": 100000,
  "status": "APPROVED",
  "currency": "KRW",
  "paymentMethod": "CARD",
  "paymentProvider": "MOCK",
  "approvedAt": "2026-09-22T01:00:00+09:00",
  "canceledAt": null
}
```

| 구분 | 값 |
| --- | --- |
| 상태 | `READY`, `PROCESSING`, `APPROVED`, `FAILED`, `CANCELED`, `UNKNOWN` |
| 결제 수단 | `CARD`, `EASY_PAY`, `OTHER` |
| Provider | `MOCK`, `TOSS` |

결제 승인 요청은 `{"paymentKey":"<payment-key>"}`입니다. 현재 DTO에는 `paymentKey` 필수 Bean Validation이 선언되어 있지 않습니다. 
응답: `paymentId`, `status`, `failureCode`

### Notification

| Query | 필수 | 기본값/값 |
| --- | --- | --- |
| `notificationType` | X | `RESERVATION_CONFIRMED`, `RESERVATION_FAILED` |
| `readStatus` | X | `UNREAD`, `READ` |
| `page` | X | 0 |
| `size` | X | 10 |
| `sort` | X | `createdAt,DESC` |

```json
{
  "notificationId": "00000000-0000-0000-0000-000000000000",
  "userId": 1,
  "notificationType": "RESERVATION_CONFIRMED",
  "title": "알림 제목",
  "content": "알림 내용",
  "readStatus": "UNREAD",
  "createdAt": "2026-09-22T01:00:00Z"
}
```

읽음 처리 응답에는 `reservationId`, `reservationNumber`, `lastViewedAt`이 추가됩니다.

---

## 4. Swagger 참고 URL

| 구분 | URL | 확인할 수 있는 내용 |
| --- | --- | --- |
| 통합 Swagger UI | `http://localhost:8000/swagger-ui/index.html` | 서비스별 API 선택, 요청·응답 스키마, API 호출 |
| Platform 명세 | `http://localhost:8000/openapi/platform` | 인증, 사용자, 주최자, 공연·회차 API의 OpenAPI JSON |
| Ticketing 명세 | `http://localhost:8000/openapi/ticketing` | 대기열, 좌석, 예매 API의 OpenAPI JSON |
| Payment & Notification 명세 | `http://localhost:8000/openapi/payment-notification` | 결제, 알림 API의 OpenAPI JSON |

| 확인 항목 | 확인 기준                                          |
| --- |------------------------------------------------|
| 서비스 선택 | Swagger UI에서 Platform, Ticketing, Payment & Notification을 선택할 수 있습니다. |
| 내부 API 제외 | `/api/v1/internal/**` 경로는 Swagger에 포함하지 않았습니다. |
| 인증 적용 | 공개 API는 인증 없이 호출할 수 있고, 보호 API는 미인증 시 `401`을 반환합니다. |
| 신뢰 헤더 제외 | `X-User-Id`, `X-User-Role`이 사용자 입력 파라미터로 표시되지 않습니다. |

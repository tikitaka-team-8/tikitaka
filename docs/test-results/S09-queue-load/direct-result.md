# S09 Queue 등록 API 직접 호출 결과

## 범위와 판정

기존 Gateway 경유 S09 결과는 [local-result.md](local-result.md)에 유지한다.
이번 자료는 이슈 #115의 Gateway 우회 후속 검증과 담당자 추가 진단이다.
성능 SLO는 별도로 합의하지 않았으므로 p95/p99만으로 운영 적합성을 판정하지 않는다.
아래 성공/실패는 **요청 5초 timeout과 등록 성공 응답** 기준이며, 클라이언트 성공과 서버 Redis 반영은 구분한다.

| 구분 | 수행 내용 |
|---|---|
| 멘토 요청 | 기존 데이터 재사용, 1,000 VU·5초 timeout, Ticketing 등록 API 직접 호출, HTTP 성공/실패·p95/p99, CPU·Tomcat·Redis 관측 |
| 담당자 추가 진단 | TCP backlog 비교, Platform 정상 stub·500ms 지연·503 오류 주입, 2,000/10,000 VU, 30초 분산 유입 및 최대 연결 수 비교 |

호출 경로는 `k6 → Ticketing Queue 등록 API → Platform 판매 상태 조회 → Redis`다.
Gateway만 제외했으며 Platform 동기 조회는 실제 실행한다. stub 실험만 호출 대상을 fixture 응답 서버로 교체한다.
직접 호출은 테스트 전용 사용자 헤더를 사용하며 운영 인증 경로의 검증이 아니다.
Seat 호출, 입장 후 구매 흐름, Scheduler 단독 성능·토큰 정합성 실험을 이번에 재수행한 것은 아니다.

## 실행 환경과 자료 출처

- Windows / Docker Desktop의 단일 로컬 환경. Docker VM 메모리 약 15.41GiB. k6와 서비스가 호스트 자원을 공유한다.
- Ticketing JDK 21, Feign 실제 구현 `feign.Client$Default`.
- 기본 비교: 실제 Platform 유지, Ticketing 재생성 후 180초 대기, 요청 timeout 5초, 회차별 승인 상한·batch 50.
- 추가 비교의 backlog는 1,000. 최대 연결 수는 명시한 경우 외에는 8,192.
- k6 이미지 digest는 runner에 고정. 실제 처리 RPS와 VU 수는 서로 다른 값이다.
- 아래 2026-09-20 대표 실행은 같은 Ticketing 이미지로 측정했다.
  - 이미지: `sha256:587538bc50a2f735d7953d68f29a03393f802a5a5bbd85c472d20b1d070dad27`
  - 소스 manifest SHA-256: `3f6eeb929849af1ae6f24343c60526459c57cffa1e6b54bf984ce202f5bf2c79`
- 당시 실행 코드는 미커밋 변경을 포함한 스냅샷이다. HEAD SHA만으로 측정 소스 전체를 식별하지 않는다.
- 이 PR은 최신 develop 통합, 테스트 전용 계측 활성화 및 자료 정리를 포함한다. 아래 수치를 최종 PR commit에서 새로 측정한 결과로 표기하지 않는다.
- 원본 result / TCP delta / Tomcat sample / k6 log는 로컬 artifacts에 보존한다. 이 문서에는 필요한 수치만 옮겼으며 원본 로그·계정·개인 경로는 제출하지 않는다.

## 멘토 요청: 1,000 VU 직접 호출

대표 실행(2026-09-20, 실제 Platform, backlog 1,000):

| 등록 성공 | 실패 | HTTP 평균 | HTTP p95 | HTTP p99 | Platform 호출 |
|---|---|---|---|---|---|
| 1,000/1,000 | 0 | 612.9ms | 1,130.4ms | 1,160.9ms | 1,000회, 평균 18.3ms, 통신 timeout 0 |

초기 직접 호출에서는 client timeout과 Ticketing TCP overflow가 관측됐다.
2026-09-19 backlog 100 / 1,000을 각각 3회 비교했을 때 두 조건 모두 HTTP 등록은 성공했다.
backlog 100에서는 실행별 overflow 584·792·647, backlog 1,000에서는 모두 0이었다.
따라서 연결 수용 지연 완화의 근거이며, 원래의 모든 간헐 timeout이 해결됐다는 증거로 쓰지 않는다.

## 담당자 추가 진단: Platform 의존성

2026-09-20의 각 조건 1회 결과다. stub도 Feign·HTTP 통신을 유지하므로 Kafka 또는 로컬 상태 복제의 성능을 측정한 것이 아니다.

| 조건 | 등록 성공 | HTTP p95 / p99 | Platform 단계 평균 | 해석 |
|---|---|---|---|---|
| 실제 Platform | 1,000/1,000 | 1,130.4 / 1,160.9ms | 18.3ms | 정상 기준 |
| 정상 stub | 1,000/1,000 | 1,001.0 / 1,045.4ms | 14.0ms | HTTP 평균 605.6ms. 정상 Platform의 지배적 병목 증거 부족 |
| stub 응답 500ms 지연 | 1,000/1,000 | 2,872.0 / 2,910.9ms | 516.1ms | 의존 서비스 지연 전파 |
| stub HTTP 503 | 0/1,000 | 620.1 / 671.2ms | 22.6ms | Queue 응답 전부 502, Redis 생성 단계 0회, WAITING/ACTIVE 0 |

503 주입 시 판매 상태를 확인하지 못해 신규 등록을 거절했다. `response` transport counter는 HTTP 응답 수신 수이며 2xx 성공만을 뜻하지 않는다.
고의 장애 주입의 k6 FAIL과 예기치 않은 애플리케이션 장애를 구분한다.
과거 85건의 504와 이후 부하에서의 connect timeout은 관련 후보이나, 과거 실행에는 동일한 상세 계측이 없어 같은 원인으로 확정하지 않는다.
Kafka 기반 판매 상태 복제는 검토만 했다. 판매 중단 정보 반영 지연·복구 정책과 다른 도메인 협의가 필요하여 이번 구현에는 포함하지 않는다.

## 담당자 추가 진단: 등록 규모와 연결 한도

모든 행은 2026-09-20, 요청 timeout 5초, backlog 1,000이다.

| 조건 | 클라이언트 성공 | timeout | HTTP p95 / p99 | Ticketing overflow | Platform connect timeout |
|---|---:|---:|---|---:|---:|
| 2,000 VU 순간, max 8,192 | 2,000 | 0 | 1,875.3 / 1,899.5ms | 43 | 0 |
| 10,000 VU 순간, 실제 Platform, max 8,192 | 4,496 | 5,504 | 5,001.5 / 5,039.0ms | 3,170 | 19 |
| 10,000 VU 순간, 정상 stub, max 8,192 | 5,549 | 4,451 | 4,961.1 / 4,979.3ms | 2,475 | 0 |
| 10,000 VU, 30초 분산, 실제 Platform, max 8,192 | 8,190 | 1,810 | 5,000.4 / 5,000.9ms | 4,072 | 0 |
| 10,000 VU, 30초 분산, 실제 Platform, max 16,384 | 10,000 | 0 | 28.5 / 79.2ms | 0 | 0 |

- 순간 유입 비교에서는 Ticketing 연결 수가 두 조건 모두 8,192/8,192에 도달했다. busy thread 표본 최대는 실제 200, stub 199였다. stub에서도 대량 timeout이 남아 Platform만의 문제로 설명할 수 없다. 각 1회 비교로 차이 전체를 인과 효과로 단정하지 않는다.
- 30초 분산의 max 8,192 조건은 연결 수가 누적됐고 keep-alive 8,192개, busy thread 1개가 동시에 관측됐다. 관측용 Actuator timeout 1건이 있어 `observationComplete=false`다. k6 부하는 끝까지 완료했다.
- max 16,384 비교에서는 연결 수 최대 10,004, keep-alive 최대 10,003, busy thread 표본 최대 9, TCP overflow/Platform timeout 모두 0이었다. 등록 로직 내부 평균 5.00ms, Platform 평균 3.03ms였다.
- 동일 이미지에서 최대 연결 수만 변경한 비교는 **이 로컬 분산 유입 조건에서 연결 한도 점유가 실패 원인이었다는 근거**다. 운영 기본값 변경이나 순간 Spike 전체 해결을 의미하지 않는다. 실행 후 기존 최대 연결 수로 복구했다.
- 30초 분산은 목표 유입 약 333명/s다. 1만 요청을 같은 순간에 받은 결과로 표현하지 않는다. 위 표는 p95/p99와 실패가 함께 있는 원본 집계이며 실패 요청도 포함한다.
- 서버 반영과 클라이언트 응답은 다르다. 순간 실제/stub 실행에서 Redis WAITING+ACTIVE는 각각 9,268/9,199로 관측됐으나 클라이언트 성공은 4,496/5,549였다. timeout 뒤에도 서버 처리가 이어질 수 있다.

### 중단된 실행의 취급

앞선 30초 분산 실행은 Actuator 수집 timeout으로 runner가 k6를 중단시켜 유효한 전체 결과에서 제외했다.
수정 후 수집 오류는 `collection-errors.json`에 기록하고 부가 관측 누락으로 표시한다.
애플리케이션 요청 timeout 5초를 늘리거나 관측 실패를 0으로 채우지 않는다.

## 코드 변경과 제한

- 신규 Entry 생성 Lua에 회차 registry SADD를 포함해 생성·발견 가능 상태를 한 번에 반영한다. 별도 registry 호출을 제거하며 중복 WAITING·경합 복구 경로는 유지한다.
- Redis 명령 자체를 제거한 것은 아니다. 새 등록의 별도 애플리케이션 왕복 1회를 줄인다. 전체 API 개선율은 분리 검증하지 않아 주장하지 않는다.
- 등록 단계 timer와 Feign transport 계측은 기본 비활성화이며 공통 test Compose에서만 켠다. 사용자·회차·토큰을 메트릭 label로 넣지 않는다.
- 이 PR은 운영 Compose, 기본 연결 한도, batch·승인 상한, Platform 코드, Kafka 구조를 변경하지 않는다.
- 10만 데이터·10만 활성 사용자·분산 부하·스테이징은 이 추가 결과의 검증 범위가 아니다.
- CPU/Tomcat/Redis 표본은 짧은 순간 피크를 놓칠 수 있다. 데이터 정리는 자연 회복의 증거가 아니며 등록 전용 결과의 recovery 값은 null이다.

실행법은 [시나리오 README](../../../scripts/test-scenarios/s09-queue-load/README.md#queue-api-직접-호출과-추가-진단)를 참고한다.


## 제출 브랜치 검증

최신 develop 통합 후 Java 컴파일과 Queue·Seat JUnit 130개를 통과했다(실패·오류·skip 0).
이 중 Redis Testcontainers 통합 테스트는 23개다. stub 테스트, 등록 실패 후 다음 요청 진행 검사,
관측 timeout 처리 검사, PowerShell/Python 문법 및 공통 Compose config 검사를 수행했다.
제출 정리 중 실제 부하·이미지 빌드/교체·운영 설정 변경은 수행하지 않았다.

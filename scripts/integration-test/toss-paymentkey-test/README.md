# Toss PaymentKey 테스트 페이지

Toss Payments 테스트 결제를 진행하고 `paymentKey`를 발급받기 위한 로컬 테스트 페이지입니다.

Node.js/npm 없이 실행할 수 있으며 **Java 21 이상**이 필요합니다.

## 사전 설정

`config.js`에서 테스트에 사용할 값을 설정합니다.

- `clientKey`: Toss 테스트 Client Key (`test_gck_...`)
- `orderId`: Payment 생성 후 발급된 `orderId`
- `amount`: 결제 금액

> Secret Key는 이 폴더에 작성하지 않습니다.  
> Payment 서버의 `TOSS_SECRET_KEY` 환경변수로 관리합니다.

---

## Windows

### 실행

`toss-paymentkey-test` 폴더의 `run.bat`을 더블클릭합니다.

또는 터미널에서 실행할 수 있습니다. `run.bat` or `.\run.bat`

```bash
cd scripts/integration-test/toss-paymentkey-test
run.bat
```

정상적으로 실행되면 다음 주소로 접속합니다.
http://localhost:5173

종료
실행 중인 터미널에서 Ctrl + C를 입력합니다.

macOS
실행

터미널에서 toss-paymentkey-test 폴더로 이동합니다.
```bash
cd scripts/integration-test/toss-paymentkey-test
```

Java로 테스트 서버를 실행합니다.
```bash
java --add-modules jdk.httpserver StaticServer.java
```
정상적으로 실행되면 다음 주소로 접속합니다.

http://localhost:5173
종료

실행 중인 터미널에서 Control + C를 입력합니다.

## 결제 테스트
Payment 생성 API를 호출하여 READY 상태의 결제를 생성합니다.
생성된 Payment의 orderId를 config.js에 설정합니다.
테스트 페이지를 실행하고 http://localhost:5173에 접속합니다.
화면의 orderId와 결제 금액이 Payment 정보와 동일한지 확인합니다.
Toss 테스트 결제를 진행합니다.
결제 인증 성공 화면에서 paymentKey를 복사합니다.
Postman Environment의 paymentKey에 복사한 값을 설정합니다.
Payment 승인 API를 호출합니다.

결제 승인 성공 시 Payment 상태가 APPROVED로 변경되는지 확인합니다.

참고
Java 21 이상이 설치되어 있어야 합니다.
Node.js/npm은 필요하지 않습니다.
paymentKey는 Toss 결제 인증 후 발급되는 값입니다.
TOSS_SECRET_KEY는 Payment 서버 환경변수로 관리하며 테스트 페이지에 작성하지 않습니다.
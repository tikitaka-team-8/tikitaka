package com.tikitaka.gateway.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String AUTHENTICATED_USER_ID = "12345";
    private static final String AUTHENTICATED_USER_ROLE = "USER";
    private static final byte[] TEST_ONLY_DUMMY_JWT_SECRET_BYTES =
            "dummy-jwt-secret-for-tests-32-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_ONLY_DUMMY_JWT_SECRET =
            Base64.getEncoder().encodeToString(TEST_ONLY_DUMMY_JWT_SECRET_BYTES);
    private static final AtomicReference<Headers> DOWNSTREAM_HEADERS = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM_SERVER = startDownstreamServer();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        String downstreamUrl = "http://127.0.0.1:" + DOWNSTREAM_SERVER.getAddress().getPort();
        registry.add("auth.token.secret", () -> TEST_ONLY_DUMMY_JWT_SECRET);
        registry.add("services.platform.url", () -> downstreamUrl);
        registry.add("services.ticketing.url", () -> downstreamUrl);
        registry.add("services.payment-notification.url", () -> downstreamUrl);
    }

    @BeforeEach
    void resetCapturedHeaders() {
        DOWNSTREAM_HEADERS.set(null);
    }

    @AfterAll
    static void stopDownstreamServer() {
        DOWNSTREAM_SERVER.stop(0);
    }

    @Test
    void 보호_API에_토큰이_없으면_접근을_거부한다() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/users/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertResponseTraceId(response);
        assertThat(DOWNSTREAM_HEADERS.get()).isNull();
    }

    @Test
    void 공개_OpenAPI_프록시는_토큰_없이_접근할_수_있다() {
        ResponseEntity<String> response = restTemplate.getForEntity("/openapi/platform", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(DOWNSTREAM_HEADERS.get()).isNotNull();
    }

    @Test
    void 보호_API에_유효하지_않은_JWT를_전달하면_접근을_거부한다() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid-jwt");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertResponseTraceId(response);
        assertThat(DOWNSTREAM_HEADERS.get()).isNull();
    }

    @Test
    void 내부_API에_대한_외부_접근을_차단한다() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(createAccessToken());
        headers.set(SERVICE_KEY_HEADER, "forged-service-key");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/internal/event-sessions/test",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertResponseTraceId(response);
        assertThat(objectMapper.readTree(response.getBody()).path("code").asText()).isEqualTo("U-006");
        assertThat(DOWNSTREAM_HEADERS.get()).isNull();
    }

    @Test
    void 외부_신뢰_헤더를_제거하고_JWT의_사용자_정보만_전달한다() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(createAccessToken());
        headers.put(USER_ID_HEADER, List.of("99999", "88888"));
        headers.put(USER_ROLE_HEADER, List.of("ADMIN", "ORGANIZER"));
        headers.put(SERVICE_KEY_HEADER, List.of("forged-service-key", "second-forged-key"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Headers forwardedHeaders = DOWNSTREAM_HEADERS.get();
        assertThat(forwardedHeaders).isNotNull();
        assertThat(forwardedHeaders.get(USER_ID_HEADER)).containsExactly(AUTHENTICATED_USER_ID);
        assertThat(forwardedHeaders.get(USER_ROLE_HEADER)).containsExactly(AUTHENTICATED_USER_ROLE);
        assertThat(forwardedHeaders.get(SERVICE_KEY_HEADER)).isNull();
    }

    @Test
    void 외부_Trace_ID를_교체하고_하위_서비스와_사용자_응답에_같은_값을_전달한다() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(createAccessToken());
        headers.put(TRACE_ID_HEADER, List.of("external-trace-id", "second-external-trace-id"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String responseTraceId = response.getHeaders().getFirst(TRACE_ID_HEADER);
        assertThat(responseTraceId).isNotBlank();
        assertThat(responseTraceId).isNotEqualTo("external-trace-id");
        assertThat(responseTraceId).isNotEqualTo("second-external-trace-id");
        assertThat(UUID.fromString(responseTraceId)).isNotNull();

        Headers forwardedHeaders = DOWNSTREAM_HEADERS.get();
        assertThat(forwardedHeaders).isNotNull();
        assertThat(forwardedHeaders.get(TRACE_ID_HEADER)).containsExactly(responseTraceId);
    }

    private static HttpServer startDownstreamServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                DOWNSTREAM_HEADERS.set(exchange.getRequestHeaders());
                byte[] responseBody = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(HttpStatus.OK.value(), responseBody.length);
                exchange.getResponseBody().write(responseBody);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Gateway 테스트용 하위 서버를 시작할 수 없습니다.", exception);
        }
    }

    private String createAccessToken() {
        SecretKey secretKey = new SecretKeySpec(TEST_ONLY_DUMMY_JWT_SECRET_BYTES, "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(AUTHENTICATED_USER_ID)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .claim("tokenType", "access")
                .claim("role", AUTHENTICATED_USER_ROLE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void assertResponseTraceId(ResponseEntity<String> response) throws JsonProcessingException {
        String headerTraceId = response.getHeaders().getFirst(TRACE_ID_HEADER);
        JsonNode responseBody = objectMapper.readTree(response.getBody());

        assertThat(headerTraceId).isNotBlank();
        assertThat(responseBody.path("traceId").asText()).isEqualTo(headerTraceId);
    }
}

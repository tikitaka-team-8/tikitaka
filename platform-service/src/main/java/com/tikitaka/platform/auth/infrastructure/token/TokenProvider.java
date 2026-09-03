package com.tikitaka.platform.auth.infrastructure.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tikitaka.platform.user.domain.UserRole;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class TokenProvider {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ROLE_CLAIM = "role";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";
    private static final int MINIMUM_SECRET_KEY_BYTES = 32;
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final TokenProperties tokenProperties;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom;

    public TokenProvider(TokenProperties tokenProperties) {
        this.tokenProperties = tokenProperties;
        this.jwtEncoder = createJwtEncoder(tokenProperties.secret());
        this.secureRandom = new SecureRandom();
    }

    public String createAccessToken(Long userId, UserRole role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenProperties.accessTokenExpiration());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim(ROLE_CLAIM, role.name())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String createRefreshToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(SHA_256);
            byte[] hash = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    public long getAccessTokenExpiresInSeconds() {
        return tokenProperties.accessTokenExpiration().toSeconds();
    }

    public long getRefreshTokenExpiresInSeconds() {
        return tokenProperties.refreshTokenExpiration().toSeconds();
    }

    private JwtEncoder createJwtEncoder(String encodedSecret) {
        byte[] secretBytes;

        try {
            secretBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT Secret은 Base64 형식이어야 합니다.", exception);
        }

        if (secretBytes.length < MINIMUM_SECRET_KEY_BYTES) {
            throw new IllegalStateException("JWT Secret은 32바이트 이상이어야 합니다.");
        }

        SecretKey secretKey = new SecretKeySpec(secretBytes, HMAC_SHA_256);
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }
}

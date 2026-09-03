package com.tikitaka.platform.auth.application;

import com.tikitaka.platform.auth.exception.AuthErrorCode;
import com.tikitaka.platform.auth.infrastructure.token.RefreshTokenRepository;
import com.tikitaka.platform.auth.infrastructure.token.TokenProvider;
import com.tikitaka.platform.auth.presentation.dto.AuthLoginRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthLoginResponse;
import com.tikitaka.platform.auth.presentation.dto.AuthLogoutRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthReissueRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthReissueResponse;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.domain.UserStatus;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String USER_EMAIL_UNIQUE_CONSTRAINT = "uq_user_email_lower";
    private static final String USER_NICKNAME_UNIQUE_CONSTRAINT = "uq_user_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthSignupResponse signUp(AuthSignupRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicateNickname(request.nickname());

        User user = User.signUp(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.nickname(),
                request.phone()
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);
            log.info("회원가입 완료: userId={}", savedUser.getId());
            return AuthSignupResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
        }

        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenProvider.createRefreshToken();
        String refreshTokenHash = tokenProvider.hashRefreshToken(refreshToken);

        refreshTokenRepository.save(
                user.getId(),
                refreshTokenHash,
                tokenProvider.getRefreshTokenExpiresInSeconds()
        );

        log.info("로그인 및 토큰 발급 완료: userId={}, role={}", user.getId(), user.getRole());

        return AuthLoginResponse.of(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpiresInSeconds()
        );
    }

    public AuthReissueResponse reissue(AuthReissueRequest request) {
        String refreshTokenHash = tokenProvider.hashRefreshToken(request.refreshToken());
        Long userId = refreshTokenRepository.findUserId(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
        }

        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getRole());

        log.info("Access Token 재발급 완료: userId={}", user.getId());

        return AuthReissueResponse.of(
                accessToken,
                request.refreshToken(),
                tokenProvider.getAccessTokenExpiresInSeconds()
        );
    }

    public void logout(Long authenticatedUserId, AuthLogoutRequest request) {
        String refreshTokenHash = tokenProvider.hashRefreshToken(request.refreshToken());
        refreshTokenRepository.deleteIfOwnedBy(authenticatedUserId, refreshTokenHash);
        log.info("로그아웃 처리 완료: userId={}", authenticatedUserId);
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }
    }

    private RuntimeException translateDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        ConstraintViolationException constraintViolation = findConstraintViolation(exception);

        if (constraintViolation == null) {
            return exception;
        }

        String constraintName = constraintViolation.getConstraintName();

        if (USER_EMAIL_UNIQUE_CONSTRAINT.equals(constraintName)) {
            return new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (USER_NICKNAME_UNIQUE_CONSTRAINT.equals(constraintName)) {
            return new BusinessException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        return exception;
    }

    private ConstraintViolationException findConstraintViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation;
            }

            cause = cause.getCause();
        }

        return null;
    }
}

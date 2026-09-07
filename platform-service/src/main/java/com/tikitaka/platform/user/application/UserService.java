package com.tikitaka.platform.user.application;

import com.tikitaka.platform.auth.exception.AuthErrorCode;
import com.tikitaka.platform.auth.infrastructure.token.RefreshTokenRepository;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.domain.UserStatus;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import com.tikitaka.platform.user.presentation.dto.UserPasswordUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserService {

    private static final String USER_NICKNAME_UNIQUE_CONSTRAINT = "uq_user_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserProfileResponse getMyProfile(Long authenticatedUserId) {
        User user = getActiveUser(authenticatedUserId);

        log.debug("회원 정보 조회 완료: userId={}", authenticatedUserId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileUpdateResponse updateMyProfile(
            Long authenticatedUserId,
            UserProfileUpdateRequest request
    ) {
        validateUpdateFieldExists(request);

        User user = getActiveUser(authenticatedUserId);

        validateDuplicateNickname(user, request);

        String updatedName = request.hasName()
                ? request.name()
                : user.getName();

        String updatedNickname = request.hasNickname()
                ? request.nickname()
                : user.getNickname();

        String updatedPhone = request.hasPhone()
                ? request.phone()
                : user.getPhone();

        user.updateProfile(
                updatedName,
                updatedNickname,
                updatedPhone
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);
            log.info("회원 정보 수정 완료: userId={}", authenticatedUserId);
            return UserProfileUpdateResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
    }

    @Transactional
    public void changePassword(
            Long authenticatedUserId,
            UserPasswordUpdateRequest request
    ) {
        User user = getActiveUser(authenticatedUserId);

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new BusinessException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {
            throw new BusinessException(UserErrorCode.INVALID_INPUT);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenRepository.deleteAllByUserId(authenticatedUserId);
        log.info("비밀번호 변경 및 Refresh Token 전체 폐기 완료: userId={}", authenticatedUserId);
    }

    private RuntimeException translateDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        ConstraintViolationException constraintViolation =
                findConstraintViolation(exception);

        if (constraintViolation == null) {
            return exception;
        }

        if (USER_NICKNAME_UNIQUE_CONSTRAINT.equals(
                constraintViolation.getConstraintName()
        )) {
            return new BusinessException(
                    UserErrorCode.NICKNAME_ALREADY_EXISTS
            );
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

    private void validateDuplicateNickname(
            User user,
            UserProfileUpdateRequest request
    ) {
        if (!request.hasNickname()) {
            return;
        }

        if (user.getNickname().equals(request.nickname())) {
            return;
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(
                    UserErrorCode.NICKNAME_ALREADY_EXISTS
            );
        }
    }

    private void validateUpdateFieldExists(UserProfileUpdateRequest request) {
        if (!request.hasAnyField()) {
            throw new BusinessException(UserErrorCode.INVALID_INPUT);
        }
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.INACTIVE_ACCOUNT);
        }

        return user;
    }
}

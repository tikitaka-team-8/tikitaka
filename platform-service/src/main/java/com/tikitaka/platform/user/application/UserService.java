package com.tikitaka.platform.user.application;

import com.tikitaka.platform.auth.exception.AuthErrorCode;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.domain.UserStatus;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String USER_NICKNAME_UNIQUE_CONSTRAINT = "uq_user_nickname";

    private final UserRepository userRepository;

    public UserProfileResponse getMyProfile(Long authenticatedUserId) {
        User user = getActiveUser(authenticatedUserId);

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
            return UserProfileUpdateResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
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

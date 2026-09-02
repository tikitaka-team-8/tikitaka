package com.tikitaka.platform.auth.application;

import com.tikitaka.platform.auth.presentation.dto.AuthSignupRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String USER_EMAIL_UNIQUE_CONSTRAINT = "uq_user_email_lower";
    private static final String USER_NICKNAME_UNIQUE_CONSTRAINT = "uq_user_nickname";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
            return AuthSignupResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
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

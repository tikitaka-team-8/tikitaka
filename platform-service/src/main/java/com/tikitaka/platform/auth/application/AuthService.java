package com.tikitaka.platform.auth.application;

import com.tikitaka.platform.auth.presentation.dto.AuthSignupRequest;
import com.tikitaka.platform.auth.presentation.dto.AuthSignupResponse;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

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

        User savedUser = userRepository.save(user);
        return AuthSignupResponse.from(savedUser);
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
}

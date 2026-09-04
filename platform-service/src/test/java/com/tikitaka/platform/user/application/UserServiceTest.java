package com.tikitaka.platform.user.application;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import com.tikitaka.platform.auth.exception.AuthErrorCode;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.domain.User;
import com.tikitaka.platform.user.domain.UserStatus;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.infrastructure.UserRepository;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void 인증된_활성_회원의_정보를_조회한다() {
        Long userId = 1L;
        Instant createdAt = Instant.parse("2026-09-04T01:00:00Z");
        User user = createUser(userId, UserStatus.ACTIVE, createdAt);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@tikitaka.com");
        assertThat(response.name()).isEqualTo("테스트");
        assertThat(response.nickname()).isEqualTo("tikitaka");
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        then(userRepository).should().findById(userId);
    }

    @Test
    void 회원이_존재하지_않으면_U_004가_발생한다() {
        Long userId = 1L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                () -> userService.getMyProfile(userId),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 정지된_회원이면_A_006이_발생한다() {
        assertInactiveAccount(UserStatus.SUSPENDED);
    }

    @Test
    void 탈퇴한_회원이면_A_006이_발생한다() {
        assertInactiveAccount(UserStatus.WITHDRAWN);
    }

    @Test
    void 입력된_필드만_수정하고_나머지_필드는_유지한다() {
        Long userId = 1L;
        Instant updatedAt = Instant.parse("2026-09-04T02:00:00Z");
        User user = createUser(userId, UserStatus.ACTIVE, updatedAt);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("newNickname");
        request.setPhone("   ");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("newNickname")).willReturn(false);
        given(userRepository.saveAndFlush(user)).willReturn(user);

        UserProfileUpdateResponse response = userService.updateMyProfile(userId, request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.name()).isEqualTo("테스트");
        assertThat(response.nickname()).isEqualTo("newNickname");
        assertThat(response.phone()).isNull();
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        then(userRepository).should().existsByNickname("newNickname");
        then(userRepository).should().saveAndFlush(user);
    }

    @Test
    void 수정할_필드가_없으면_U_003이_발생한다() {
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();

        BusinessException exception = catchThrowableOfType(
                () -> userService.updateMyProfile(1L, request),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.INVALID_INPUT);
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    void 다른_회원이_사용하는_닉네임이면_U_002가_발생한다() {
        Long userId = 1L;
        User user = createUser(
                userId,
                UserStatus.ACTIVE,
                Instant.parse("2026-09-04T02:00:00Z")
        );
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("duplicateNickname");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("duplicateNickname")).willReturn(true);

        BusinessException exception = catchThrowableOfType(
                () -> userService.updateMyProfile(userId, request),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        then(userRepository).should().findById(userId);
        then(userRepository).should().existsByNickname("duplicateNickname");
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void DB_닉네임_unique_constraint_충돌을_U_002로_변환한다() {
        Long userId = 1L;
        User user = createUser(
                userId,
                UserStatus.ACTIVE,
                Instant.parse("2026-09-04T02:00:00Z")
        );
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("newNickname");
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "닉네임 unique constraint 충돌",
                new SQLException(),
                "uq_user_nickname"
        );
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("newNickname")).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException(
                        "닉네임 unique constraint 충돌",
                        constraintViolation
                ));

        BusinessException exception = catchThrowableOfType(
                () -> userService.updateMyProfile(userId, request),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(UserErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    private void assertInactiveAccount(UserStatus status) {
        Long userId = 1L;
        User user = createUser(userId, status, Instant.parse("2026-09-04T01:00:00Z"));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        BusinessException exception = catchThrowableOfType(
                () -> userService.getMyProfile(userId),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INACTIVE_ACCOUNT);
    }

    private User createUser(Long userId, UserStatus status, Instant createdAt) {
        User user = User.signUp(
                "test@tikitaka.com",
                "encoded-password",
                "테스트",
                "tikitaka",
                "010-1234-5678"
        );
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "status", status);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        ReflectionTestUtils.setField(user, "updatedAt", createdAt);

        return user;
    }
}

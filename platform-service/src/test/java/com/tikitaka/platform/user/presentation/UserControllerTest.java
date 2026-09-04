package com.tikitaka.platform.user.presentation;

import java.time.Instant;
import java.util.List;

import com.tikitaka.platform.auth.infrastructure.SecurityConfig;
import com.tikitaka.platform.auth.infrastructure.security.AuthenticatedUser;
import com.tikitaka.platform.global.exception.BusinessException;
import com.tikitaka.platform.user.application.UserService;
import com.tikitaka.platform.user.domain.UserRole;
import com.tikitaka.platform.user.exception.UserErrorCode;
import com.tikitaka.platform.user.presentation.dto.UserProfileResponse;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void 인증된_회원의_정보를_조회한다() throws Exception {
        Long userId = 1L;
        Instant createdAt = Instant.parse("2026-09-04T01:00:00Z");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, UserRole.USER);
        UserProfileResponse response = new UserProfileResponse(
                userId,
                "test@tikitaka.com",
                "테스트",
                "tikitaka",
                "010-1234-5678",
                "USER",
                "ACTIVE",
                createdAt
        );
        given(userService.getMyProfile(userId)).willReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                authenticatedUser,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("회원 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.email").value("test@tikitaka.com"))
                .andExpect(jsonPath("$.data.name").value("테스트"))
                .andExpect(jsonPath("$.data.nickname").value("tikitaka"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-04T01:00:00Z"));

        then(userService).should().getMyProfile(userId);
    }

    @Test
    void 인증되지_않은_요청은_거부된다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().is4xxClientError());

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    void 요청의_사용자_ID가_아닌_인증_사용자_ID를_사용한다() throws Exception {
        Long authenticatedUserId = 1L;
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                authenticatedUserId,
                UserRole.USER
        );
        UserProfileResponse response = new UserProfileResponse(
                authenticatedUserId,
                "test@tikitaka.com",
                "테스트",
                "tikitaka",
                null,
                "USER",
                "ACTIVE",
                Instant.parse("2026-09-04T01:00:00Z")
        );
        given(userService.getMyProfile(authenticatedUserId)).willReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .queryParam("userId", "999")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                authenticatedUser,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(authenticatedUserId));

        then(userService).should().getMyProfile(authenticatedUserId);
    }

    @Test
    void 인증된_회원의_정보를_부분_수정한다() throws Exception {
        Long userId = 1L;
        UserProfileUpdateResponse response = new UserProfileUpdateResponse(
                userId,
                "테스트",
                "newNickname",
                null,
                Instant.parse("2026-09-04T02:00:00Z")
        );
        given(userService.updateMyProfile(
                eq(userId),
                any(UserProfileUpdateRequest.class)
        )).willReturn(response);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": " newNickname ",
                                  "phone": "   "
                                }
                                """)
                        .with(authentication(authenticationOf(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("회원 정보를 수정했습니다."))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.name").value("테스트"))
                .andExpect(jsonPath("$.data.nickname").value("newNickname"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-04T02:00:00Z"));

        then(userService).should().updateMyProfile(
                eq(userId),
                argThat(request -> {
                    assertThat(request.hasName()).isFalse();
                    assertThat(request.hasNickname()).isTrue();
                    assertThat(request.nickname()).isEqualTo("newNickname");
                    assertThat(request.hasPhone()).isTrue();
                    assertThat(request.phone()).isNull();
                    return true;
                })
        );
    }

    @Test
    void 이름을_null로_수정하면_C_002가_발생한다() throws Exception {
        Long userId = 1L;

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null}")
                        .with(authentication(authenticationOf(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C-002"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").value("이름은 null일 수 없습니다."));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    void 수정할_필드가_없으면_U_003이_발생한다() throws Exception {
        Long userId = 1L;
        given(userService.updateMyProfile(
                eq(userId),
                any(UserProfileUpdateRequest.class)
        )).willThrow(new BusinessException(UserErrorCode.INVALID_INPUT));

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(authenticationOf(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U-003"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void 인증되지_않은_회원_정보_수정_요청은_거부된다() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정 이름\"}"))
                .andExpect(status().is4xxClientError());

        then(userService).shouldHaveNoInteractions();
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, UserRole.USER),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}

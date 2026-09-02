package com.tikitaka.platform.auth.presentation.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthSignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*\\p{Punct})\\S+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함하고 공백을 포함할 수 없습니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname,

        @Size(max = 20, message = "휴대폰 번호는 20자 이하여야 합니다.")
        String phone
) {

    public AuthSignupRequest {
        email = normalizeEmail(email);
        name = trim(name);
        nickname = trim(nickname);
        phone = trimToNull(phone);
    }

    private static String normalizeEmail(String email) {
        String trimmedEmail = trim(email);
        return trimmedEmail == null ? null : trimmedEmail.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmedValue = trim(value);
        return trimmedValue == null || trimmedValue.isEmpty() ? null : trimmedValue;
    }
}

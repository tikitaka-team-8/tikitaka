package com.tikitaka.platform.user.presentation.dto;

import com.tikitaka.platform.user.presentation.validation.ValidUserProfileUpdateRequest;
import jakarta.validation.constraints.Size;

@ValidUserProfileUpdateRequest
public class UserProfileUpdateRequest {

    @Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하여야 합니다.")
    private String name;

    @Size(min = 1, max = 50, message = "닉네임은 1자 이상 50자 이하여야 합니다.")
    private String nickname;

    @Size(max = 20, message = "휴대폰 번호는 20자 이하여야 합니다.")
    private String phone;

    private boolean namePresent;
    private boolean nicknamePresent;
    private boolean phonePresent;

    public UserProfileUpdateRequest() {
    }

    public void setName(String name) {
        this.namePresent = true;
        this.name = trim(name);
    }

    public void setNickname(String nickname) {
        this.nicknamePresent = true;
        this.nickname = trim(nickname);
    }

    public void setPhone(String phone) {
        this.phonePresent = true;
        this.phone = trimToNull(phone);
    }

    public String name() {
        return name;
    }

    public String nickname() {
        return nickname;
    }

    public String phone() {
        return phone;
    }

    public boolean hasName() {
        return namePresent;
    }

    public boolean hasNickname() {
        return nicknamePresent;
    }

    public boolean hasPhone() {
        return phonePresent;
    }

    public boolean hasAnyField() {
        return namePresent || nicknamePresent || phonePresent;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmedValue = trim(value);
        return trimmedValue == null || trimmedValue.isEmpty()
                ? null
                : trimmedValue;
    }
}

package com.tikitaka.platform.user.presentation.validation;

import com.tikitaka.platform.user.presentation.dto.UserProfileUpdateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UserProfileUpdateRequestValidator implements
        ConstraintValidator<ValidUserProfileUpdateRequest, UserProfileUpdateRequest> {

    private static final String NAME_NULL_MESSAGE = "이름은 null일 수 없습니다.";
    private static final String NICKNAME_NULL_MESSAGE = "닉네임은 null일 수 없습니다.";

    @Override
    public boolean isValid(
            UserProfileUpdateRequest request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }

        boolean valid = true;

        if (request.hasName() && request.name() == null) {
            addPropertyViolation(context, "name", NAME_NULL_MESSAGE);
            valid = false;
        }

        if (request.hasNickname() && request.nickname() == null) {
            addPropertyViolation(context, "nickname", NICKNAME_NULL_MESSAGE);
            valid = false;
        }

        return valid;
    }

    private void addPropertyViolation(
            ConstraintValidatorContext context,
            String property,
            String message
    ) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}

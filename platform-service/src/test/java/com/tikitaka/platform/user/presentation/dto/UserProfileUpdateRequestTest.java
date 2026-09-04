package com.tikitaka.platform.user.presentation.dto;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileUpdateRequestTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 입력된_문자열을_trim하고_필드_입력_여부를_기록한다() throws Exception {
        UserProfileUpdateRequest request = OBJECT_MAPPER.readValue(
                """
                        {
                          "name": " 테스트 ",
                          "nickname": " tikitaka ",
                          "phone": " 010-1234-5678 "
                        }
                        """,
                UserProfileUpdateRequest.class
        );

        assertThat(request.name()).isEqualTo("테스트");
        assertThat(request.nickname()).isEqualTo("tikitaka");
        assertThat(request.phone()).isEqualTo("010-1234-5678");
        assertThat(request.hasName()).isTrue();
        assertThat(request.hasNickname()).isTrue();
        assertThat(request.hasPhone()).isTrue();
        assertThat(request.hasAnyField()).isTrue();
    }

    @Test
    void 휴대폰_번호의_null과_빈_문자열은_삭제_값으로_정규화한다() throws Exception {
        UserProfileUpdateRequest nullPhone = OBJECT_MAPPER.readValue(
                "{\"phone\":null}",
                UserProfileUpdateRequest.class
        );
        UserProfileUpdateRequest blankPhone = OBJECT_MAPPER.readValue(
                "{\"phone\":\"   \"}",
                UserProfileUpdateRequest.class
        );

        assertThat(nullPhone.hasPhone()).isTrue();
        assertThat(nullPhone.phone()).isNull();
        assertThat(blankPhone.hasPhone()).isTrue();
        assertThat(blankPhone.phone()).isNull();
    }

    @Test
    void 미입력_필드는_변경_대상으로_표시하지_않는다() throws Exception {
        UserProfileUpdateRequest request = OBJECT_MAPPER.readValue(
                "{}",
                UserProfileUpdateRequest.class
        );

        assertThat(request.hasName()).isFalse();
        assertThat(request.hasNickname()).isFalse();
        assertThat(request.hasPhone()).isFalse();
        assertThat(request.hasAnyField()).isFalse();
    }

    @Test
    void 입력된_이름과_닉네임의_null은_각_필드_오류가_된다() throws Exception {
        UserProfileUpdateRequest request = OBJECT_MAPPER.readValue(
                "{\"name\":null,\"nickname\":null}",
                UserProfileUpdateRequest.class
        );

        Map<String, String> errors = errorsOf(request);

        assertThat(errors)
                .containsEntry("name", "이름은 null일 수 없습니다.")
                .containsEntry("nickname", "닉네임은 null일 수 없습니다.");
    }

    @Test
    void 이름과_닉네임의_빈_문자열은_길이_검증에_실패한다() throws Exception {
        UserProfileUpdateRequest request = OBJECT_MAPPER.readValue(
                "{\"name\":\"   \",\"nickname\":\"   \"}",
                UserProfileUpdateRequest.class
        );

        Map<String, String> errors = errorsOf(request);

        assertThat(errors)
                .containsEntry("name", "이름은 1자 이상 50자 이하여야 합니다.")
                .containsEntry("nickname", "닉네임은 1자 이상 50자 이하여야 합니다.");
    }

    @Test
    void 각_필드의_최대_길이를_초과하면_검증에_실패한다() throws Exception {
        UserProfileUpdateRequest request = OBJECT_MAPPER.readValue(
                """
                        {
                          "name": "%s",
                          "nickname": "%s",
                          "phone": "%s"
                        }
                        """.formatted("가".repeat(51), "나".repeat(51), "1".repeat(21)),
                UserProfileUpdateRequest.class
        );

        assertThat(errorsOf(request)).containsKeys("name", "nickname", "phone");
    }

    private Map<String, String> errorsOf(UserProfileUpdateRequest request) {
        Set<ConstraintViolation<UserProfileUpdateRequest>> violations =
                validator.validate(request);

        return violations.stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));
    }
}

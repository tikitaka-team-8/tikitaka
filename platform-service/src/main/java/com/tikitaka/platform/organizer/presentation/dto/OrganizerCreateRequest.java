package com.tikitaka.platform.organizer.presentation.dto;

import com.tikitaka.platform.organizer.application.command.OrganizerCreateCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizerCreateRequest(
    @NotBlank(message = "주최자명은 필수입니다.")
    @Size(max = 100, message = "주최자명은 100자 이하여야 합니다.")
    String name,

    @NotBlank(message = "대표자명은 필수입니다.")
    @Size(max = 100, message = "대표자명은 100자 이하여야 합니다.")
    String representativeName,

    @NotBlank(message = "담당자 이메일은 필수입니다.")
    @Email(message = "담당자 이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "담당자 이메일은 100자 이하여야 합니다.")
    String contactEmail,

    @Size(
        min = 1,
        max = 20,
        message = "담당자 연락처는 1자 이상 20자 이하여야 합니다."
    )
    String contactPhone,

    @Size(max = 20, message = "주최자 소개는 20자 이하여야 합니다.")
    String description
) {

    public OrganizerCreateCommand toCommand(Long userId) {
        return new OrganizerCreateCommand(
            userId,
            name,
            representativeName,
            contactEmail,
            contactPhone,
            description
        );
    }
}

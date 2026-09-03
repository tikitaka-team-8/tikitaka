package com.tikitaka.platform.organizer.presentation.dto;

import com.tikitaka.platform.organizer.application.command.OrganizerUpdateCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizerUpdateRequest(
    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "주최자명은 공백일 수 없습니다.")
    String name,

    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "대표자명은 공백일 수 없습니다.")
    String representativeName,

    @Email
    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "이메일은 공백일 수 없습니다.")
    String contactEmail,

    @Size(min = 8, max = 20)
    @Pattern(regexp = ".*\\S.*", message = "연락처는 공백일 수 없습니다.")
    String contactPhone,

    @Size(max = 1000)
    String description
) {
    public OrganizerUpdateCommand toCommand(Long userId) {
        return new OrganizerUpdateCommand(
            userId,
            name,
            representativeName,
            contactEmail,
            contactPhone,
            description
        );
    }
}

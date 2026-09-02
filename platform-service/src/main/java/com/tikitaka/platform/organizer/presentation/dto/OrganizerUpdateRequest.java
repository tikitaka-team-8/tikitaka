package com.tikitaka.platform.organizer.presentation.dto;

import com.tikitaka.platform.organizer.application.command.OrganizerUpdateCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizerUpdateRequest(
    @NotBlank
    @Size(min = 1, max = 100)
    String name,

    @NotBlank
    @Size(min = 1, max = 100)
    String representativeName,

    @NotBlank
    @Email
    @Size(max = 100)
    String contactEmail,

    @Size(min = 8, max = 20)
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

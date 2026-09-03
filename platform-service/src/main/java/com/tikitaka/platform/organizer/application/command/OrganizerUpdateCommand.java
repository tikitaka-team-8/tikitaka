package com.tikitaka.platform.organizer.application.command;

public record OrganizerUpdateCommand(
    Long userId,
    String name,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String description
) {
}

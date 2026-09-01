package com.tikitaka.platform.organizer.application.command;

public record OrganizerCreateCommand(
    Long userId,
    String name,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String description
) {
}

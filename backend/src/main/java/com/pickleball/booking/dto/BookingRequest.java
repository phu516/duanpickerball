package com.pickleball.booking.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record BookingRequest(@NotNull Long courtId,
                             @NotBlank String name,
                             @NotBlank @Pattern(regexp = "[0-9+() .-]{9,16}") String phone,
                             @Email String email,
                             @Size(max = 500) String note,
                             @NotNull OffsetDateTime startAt,
                             @NotNull OffsetDateTime endAt) {}

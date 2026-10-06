package com.pickleball.court.dto;

import java.math.BigDecimal;

public record CourtResponse(Long id, String code, String name, String surface,
                            boolean indoor, String description, String status,
                            BigDecimal startingPrice, boolean locked) {}

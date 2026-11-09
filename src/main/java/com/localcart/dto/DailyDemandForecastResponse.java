package com.localcart.dto;

import java.time.LocalDate;

public record DailyDemandForecastResponse(
        LocalDate date,
        double expectedUnits
) {
}

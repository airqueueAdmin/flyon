package com.airplanehome.flight.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeSupport {
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private TimeSupport() {
    }

    public static LocalDateTime nowKst() {
        return LocalDateTime.now(KST_ZONE);
    }
}

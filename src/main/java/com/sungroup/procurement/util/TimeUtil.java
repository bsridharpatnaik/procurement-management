package com.sungroup.procurement.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static LocalDateTime nowIST() {
        return LocalDateTime.now(IST_ZONE);
    }

    public static String formatIST(LocalDateTime dateTime) {
        return dateTime.atZone(IST_ZONE).format(IST_FORMATTER);
    }
}
package com.fixconnect.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        Instant timestamp) {
}

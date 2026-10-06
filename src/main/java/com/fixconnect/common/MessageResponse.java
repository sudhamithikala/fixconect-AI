package com.fixconnect.common;

/** Simple success / message payload (mirrors the {success, message} shape used by the original Express server). */
public record MessageResponse(boolean success, String message) {
    public static MessageResponse ok(String message) {
        return new MessageResponse(true, message);
    }
}

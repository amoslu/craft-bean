package com.craftbean.common;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final int status;
    private final int code;

    public AppException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AppException unauthorized(String msg) {
        return new AppException(401, 401, msg);
    }
}

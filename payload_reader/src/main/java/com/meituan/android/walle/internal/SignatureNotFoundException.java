package com.meituan.android.walle.internal;


public class SignatureNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public SignatureNotFoundException(String message) {
        super(message);
    }

    public SignatureNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

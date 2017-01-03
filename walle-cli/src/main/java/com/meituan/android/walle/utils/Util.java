package com.meituan.android.walle.utils;

/**
 * Created by chentong on 21/11/2016.
 */

public final class Util {
    private Util() {
        super();
    }

    public static boolean isTextEmpty(final String text) {
        return text == null || text.length() == 0;
    }
}

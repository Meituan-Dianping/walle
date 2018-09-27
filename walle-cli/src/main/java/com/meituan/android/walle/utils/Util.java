package com.meituan.android.walle.utils;

import java.io.File;


public final class Util {
    private Util() {
        super();
    }

    public static boolean isTextEmpty(final String text) {
        return text == null || text.length() == 0;
    }
    public static File removeDirInvalidChar(final File file) {
        if (System.getProperties().getProperty("os.name").toUpperCase().startsWith("WINDOWS")) {
            final String newFileName = file.getName().replaceAll("\"", "");
            return new File(file.getParent(), newFileName);
        }
        return file;
    }
}

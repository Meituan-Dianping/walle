package com.meituan.android.walle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;


public class WalleCommandLine {
    @Parameter(names = {"-v", "--version"}, description = "show walle version")
    private boolean showVersion;

    @Parameter(names = {"-h", "--help"}, description = "show walle command line help")
    private boolean showHelp;

    public void parse(final JCommander commander) {
        if (showVersion) {
            System.out.println(getVersion());
            return;
        }
        if (showHelp) {
            commander.usage();
        }
    }

    private static String getVersion() {
        try {
            final Enumeration resEnum = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resEnum.hasMoreElements()) {
                try {
                    final URL url = (URL) resEnum.nextElement();
                    final InputStream is = url.openStream();
                    if (is != null) {
                        final Manifest manifest = new Manifest(is);
                        final Attributes mainAttribs = manifest.getMainAttributes();
                        final String version = mainAttribs.getValue("Walle-Version");
                        if (version != null) {
                            return version;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }
}

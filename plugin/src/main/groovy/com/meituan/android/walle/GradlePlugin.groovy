package com.meituan.android.walle

import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.SigningConfig
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

class GradlePlugin implements org.gradle.api.Plugin<Project> {

    public static final String sPluginExtensionName = "walle";

    @Override
    void apply(Project project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new ProjectConfigurationException("Plugin requires the 'com.android.application' plugin to be configured.", null);
        }

        String version = null
        try {
            def clazz = Class.forName("com.android.builder.Version")
            def field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            field.setAccessible(true)
            version = field.get(null)
        } catch (ClassNotFoundException ignore) {
        } catch (NoSuchFieldException ignore) {
        }
        if (version == null) {
            try {
                def clazz = Class.forName("com.android.builder.model.Version")
                def field = clazz.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
                field.setAccessible(true)
                version = field.get(null)
            } catch (ClassNotFoundException ignore) {
            } catch (NoSuchFieldException ignore) {
            }
        }

        if (version != null && versionCompare(version, "2.2.0") < 0) {
            throw new ProjectConfigurationException("Plugin requires the 'com.android.tools.build:gradle' version 2.2.0 or above to be configured.", null);
        }

//        project.dependencies {
//            compile 'com.meituan.android.walle:library:' + getVersion()
//        }

        applyExtension(project);

        applyTask(project);
    }

    void applyExtension(Project project) {
        project.extensions.create(sPluginExtensionName, Extension, project);
    }


    void applyTask(Project project) {
        project.afterEvaluate {
            project.android.applicationVariants.all { BaseVariant variant ->
                def variantName = variant.name.capitalize();

                if (!isV2SignatureSchemeEnabled(variant)) {
                    throw new ProjectConfigurationException("Plugin requires 'APK Signature Scheme v2 Enabled' for ${variant.name}.", null);
                }

                ChannelMaker channelMaker = project.tasks.create("assemble${variantName}Channels", ChannelMaker);
                channelMaker.targetProject = project;
                channelMaker.variant = variant;
                channelMaker.setup();

                if (variant.hasProperty('assembleProvider')) {
                    channelMaker.dependsOn variant.assembleProvider.get()
                } else {
                    channelMaker.dependsOn variant.assemble
                }
            }
        }
    }

    SigningConfig getSigningConfig(BaseVariant variant) {
        return variant.buildType.signingConfig == null ? variant.mergedFlavor.signingConfig : variant.buildType.signingConfig;
    }

    boolean isV2SignatureSchemeEnabled(BaseVariant variant) throws GradleException {
        def signingConfig = getSigningConfig(variant);
        if (signingConfig == null || !signingConfig.isSigningReady()) {
            return false;
        }

        // check whether APK Signature Scheme v2 is enabled.
        if (signingConfig.hasProperty("v2SigningEnabled") &&
                signingConfig.v2SigningEnabled == true) {
            return true;
        }

        return false;
    }

    /**
     * Compares two version strings.
     *
     * Use this instead of String.compareTo() for a non-lexicographical
     * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
     *
     * @note It does not work if "1.10" is supposed to be equal to "1.10.0".
     *
     * @param str1 a string of ordinal numbers separated by decimal points.
     * @param str2 a string of ordinal numbers separated by decimal points.
     * @return The result is a negative integer if str1 is _numerically_ less than str2.
     *         The result is a positive integer if str1 is _numerically_ greater than str2.
     *         The result is zero if the strings are _numerically_ equal.
     */
    private static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("-")[0].split("\\.");
        String[] vals2 = str2.split("-")[0].split("\\.");
        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }

        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        }

        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        else {
            return Integer.signum(vals1.length - vals2.length);
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

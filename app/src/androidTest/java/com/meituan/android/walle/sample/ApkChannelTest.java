package com.meituan.android.walle.sample;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import com.meituan.android.walle.ChannelInfo;
import com.meituan.android.walle.ChannelReader;
import com.meituan.android.walle.ChannelWriter;
import com.meituan.android.walle.SignatureNotFoundException;
import com.meituan.android.walle.WalleChannelReader;

import junit.framework.Assert;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * @author Kaede
 * @since 2017/2/14
 */

@RunWith(AndroidJUnit4.class)
public class ApkChannelTest {

    private final String[] testChannels = new String[]{
            "taisetsu", "na", "hitoto", "itsuka", "mata", "meguriaemasu", "youni!"};
    private Context mContext;

    @Before
    public void setUp() {
        // Context of the app under test.
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testReadCurrentApkChannel() throws Exception {
        // GetApk
        String channel = WalleChannelReader.getChannel(mContext);
        Assert.assertTrue(TextUtils.isEmpty(channel));
    }

    @Test
    public void testWriteAndRemoveChannel() throws Exception {
        // GetApk
        ApplicationInfo info = mContext.getApplicationInfo();
        FileInputStream in = new FileInputStream(new File(info.sourceDir));
        File output = new File(mContext.getExternalCacheDir(), "no-channel.apk");
        FileUtils.deleteQuietly(output);
        FileOutputStream out = new FileOutputStream(output);
        IOUtils.copy(in, out);

        String originMD5 = DigestUtils.md5(output);
        String channel = readApkChannel(output);
        Assert.assertTrue(TextUtils.isEmpty(channel));

        // Test Write & Remove channel.
        for (String item : testChannels) {
            writeChannel(item, output);
            channel = readApkChannel(output);
            Assert.assertTrue(channel.equals(item));

            removeApkChannel(output);
            channel = readApkChannel(output);
            String md5 = DigestUtils.md5(output);
            Assert.assertTrue(TextUtils.isEmpty(channel));
            Assert.assertTrue(md5.equals(originMD5));
        }
    }

    private String readApkChannel(File apkFile) {
        ChannelInfo channelInfo = ChannelReader.get(apkFile);
        return channelInfo == null ? null : channelInfo.getChannel();
    }

    private void writeChannel(String channel, File output) throws java.io.IOException, SignatureNotFoundException {
        ChannelWriter.put(output, channel);
    }

    private void removeApkChannel(File apkFile) throws java.io.IOException, SignatureNotFoundException {
        ChannelWriter.remove(apkFile);
    }
}
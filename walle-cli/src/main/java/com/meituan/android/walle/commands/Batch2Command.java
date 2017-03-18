package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.google.gson.Gson;
import com.meituan.android.walle.ChannelWriter;
import com.meituan.android.walle.SignatureNotFoundException;
import com.meituan.android.walle.WalleConfig;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Parameters(commandDescription = "channel apk batch production")
public class Batch2Command implements IWalleCommand {

    @Parameter(required = true, description = "inputFile [outputDirectory]", arity = 2, converter = FileConverter.class)
    private List<File> files;

    @Parameter(names = {"-f", "--configFile"}, description = "config file (json)")
    private File configFile;

    @Override
    public void parse() {
        final File inputFile = files.get(0);
        File outputDir = null;
        if (files.size() == 2) {
            outputDir = files.get(1);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
        } else {
            outputDir = inputFile.getParentFile();
        }

        if (configFile != null) {
            try {
                final WalleConfig config = new Gson().fromJson(new FileReader(configFile), WalleConfig.class);
                final Map<String, String> defaultExtraInfo = config.getDefaultExtraInfo();
                final List<WalleConfig.ChannelInfo> channelInfoList = config.getChannelInfoList();
                for (WalleConfig.ChannelInfo channelInfo : channelInfoList) {
                    Map<String, String> extraInfo = channelInfo.getExtraInfo();
                    if (extraInfo == null) {
                        extraInfo = defaultExtraInfo;
                    }
                    generateChannelApk(inputFile, outputDir, channelInfo.getChannel(), channelInfo.getAlias(), extraInfo);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void generateChannelApk(final File inputFile, final File outputDir, final String channel, final String alias, final Map<String, String> extraInfo) {
        final String channelName = alias == null ? channel : alias;
        final String name = FilenameUtils.getBaseName(inputFile.getName());
        final String extension = FilenameUtils.getExtension(inputFile.getName());
        final String newName = name + "_" + channelName + "." + extension;
        final File channelApk = new File(outputDir, newName);
        try {
            FileUtils.copyFile(inputFile, channelApk);
            ChannelWriter.put(channelApk, channel, extraInfo);
        } catch (IOException | SignatureNotFoundException e) {
            e.printStackTrace();
        }
    }
}

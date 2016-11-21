package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.PayloadWriter;
import com.meituan.android.walle.utils.CommaSeparatedKeyValueConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Parameters(commandDescription = "write lots of channel apk")
public class WriteChannelsCommand implements IWalleCommand{

    @Parameter(required = true, description = "inputFile [outputDirectory]", arity = 2, converter = FileConverter.class)
    private List<File> files;

    @Parameter(names = {"-e", "--extraInfo"}, converter = CommaSeparatedKeyValueConverter.class, description = "Comma-separated list of key-value info, e.g.: -e time=1,type=android")
    private Map<String, String> extraInfo;

    @Parameter(names = {"-c", "--channelList"}, description = "Comma-separated list of channel, e.g.: meituan,xiaomi")
    private String channelList;

    @Override
    public void parse() {
        File inputFile = files.get(0);
        File outputFile = null;
        if (files.size() == 2) {
            outputFile = files.get(1);
            if (!outputFile.isDirectory()) {
                throw new RuntimeException("output need directory");
            }
        } else {
            outputFile = inputFile.getParentFile();
        }

        String[] channels = channelList.split(",");
        for (String channel : channels) {
            String name = FilenameUtils.getBaseName(inputFile.getName());
            String extension = FilenameUtils.getExtension(inputFile.getName());
            String newName = name + "_" + channel + "." + extension;
            File channelApk = new File(outputFile, newName);
            try {
                FileUtils.copyFile(inputFile, channelApk);
                PayloadWriter.putChannel(channelApk, channel, extraInfo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

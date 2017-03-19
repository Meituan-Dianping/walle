package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.ChannelWriter;
import com.meituan.android.walle.SignatureNotFoundException;
import com.meituan.android.walle.utils.CommaSeparatedKeyValueConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Parameters(commandDescription = "put channel info into apk")
public class PutCommand implements IWalleCommand{

    @Parameter(required = true, description = "inputFile [outputFile]", arity = 2, converter = FileConverter.class)
    private List<File> files;

    @Parameter(names = {"-e", "--extraInfo"}, converter = CommaSeparatedKeyValueConverter.class, description = "Comma-separated list of key=value info, eg: -e time=1,type=android")
    private Map<String, String> extraInfo;

    @Parameter(names = {"-c", "--channel"}, description = "single channel, eg: -c meituan")
    private String channel;

    @Override
    public void parse() {
        final File inputFile = files.get(0);
        File outputFile = null;
        if (files.size() == 2) {
            outputFile = files.get(1);
        } else {
            final String name = FilenameUtils.getBaseName(inputFile.getName());
            final String extension = FilenameUtils.getExtension(inputFile.getName());
            final String newName = name + "_" + channel + "." + extension;
            outputFile = new File(inputFile.getParent(), newName);
        }
        if (inputFile.equals(outputFile)) {
            try {
                ChannelWriter.put(outputFile, channel, extraInfo);
            } catch (IOException | SignatureNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                FileUtils.copyFile(inputFile, outputFile);
                ChannelWriter.put(outputFile, channel, extraInfo);
            } catch (IOException | SignatureNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}

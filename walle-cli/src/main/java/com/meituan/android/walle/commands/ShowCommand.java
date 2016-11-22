package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.ChannelInfo;
import com.meituan.android.walle.PayloadReader;
import com.meituan.android.walle.utils.Fun1;

import java.io.File;
import java.util.List;
import java.util.Map;


@Parameters(commandDescription = "get channel info from apk and show all by default")
public class ShowCommand implements IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", converter = FileConverter.class, variableArity = true)
    private List<File> files;

    @Parameter(names = {"-e", "--extraInfo"}, description = "get channel extra info")
    private boolean extraInfo = false;

    @Parameter(names = {"-c", "--channel"}, description = "get channel")
    private boolean channel = false;

    @Override
    public void parse() {
        if(extraInfo) {
            printInfo(new Fun1<File, String>() {
                @Override
                public String apply(File file) {
                    ChannelInfo channelInfo = PayloadReader.getChannelInfo(file);
                    if (channelInfo == null) {
                        return "";
                    }
                    Map<String, String> map = channelInfo.getExtraInfo();
                    return map == null ? "" : map.toString();
                }
            });
            return;
        }
        if(channel) {
            printInfo(new Fun1<File, String>() {
                @Override
                public String apply(File file) {
                    ChannelInfo channelInfo = PayloadReader.getChannelInfo(file);
                    if (channelInfo == null) {
                        return "";
                    }
                    return channelInfo.getChannel();
                }
            });
            return;
        }
        printInfo(new Fun1<File, String>() {
            @Override
            public String apply(File file) {
                Map<String, String> map = PayloadReader.getChannelInfoMap(file);
                return map == null ? "" : map.toString();
            }
        });
    }
    private void printInfo(Fun1<File, String> fun) {
        for (File file : files) {
            System.out.println(file.getAbsolutePath() + " : " + fun.apply(file));
        }
    }
}

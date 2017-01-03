package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.ChannelInfo;
import com.meituan.android.walle.ChannelReader;
import com.meituan.android.walle.utils.Fun1;

import java.io.File;
import java.util.List;
import java.util.Map;


@Parameters(commandDescription = "get channel info from apk and show all by default")
public class ShowCommand implements IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", converter = FileConverter.class, variableArity = true)
    private List<File> files;

    @Parameter(names = {"-e", "--extraInfo"}, description = "get channel extra info")
    private boolean showExtraInfo;

    @Parameter(names = {"-c", "--channel"}, description = "get channel")
    private boolean shoChannel;

    @Parameter(names = {"-r", "--raw"}, description = "get raw string from Channel id")
    private boolean showRaw;

    @Override
    public void parse() {
        if (showRaw) {
            printInfo(new Fun1<File, String>() {
                @Override
                public String apply(final File file) {
                    final String rawChannelInfo = ChannelReader.getRaw(file);
                    return rawChannelInfo == null ? "" : rawChannelInfo;
                }
            });
        }
        if (showExtraInfo) {
            printInfo(new Fun1<File, String>() {
                @Override
                public String apply(final File file) {
                    final ChannelInfo channelInfo = ChannelReader.get(file);
                    if (channelInfo == null) {
                        return "";
                    }
                    final Map<String, String> map = channelInfo.getExtraInfo();
                    return map == null ? "" : map.toString();
                }
            });
            return;
        }
        if (shoChannel) {
            printInfo(new Fun1<File, String>() {
                @Override
                public String apply(final File file) {
                    final ChannelInfo channelInfo = ChannelReader.get(file);
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
            public String apply(final File file) {
                final Map<String, String> map = ChannelReader.getMap(file);
                return map == null ? "" : map.toString();
            }
        });
    }

    private void printInfo(final Fun1<File, String> fun) {
        for (File file : files) {
            System.out.println(file.getAbsolutePath() + " : " + fun.apply(file));
        }
    }
}

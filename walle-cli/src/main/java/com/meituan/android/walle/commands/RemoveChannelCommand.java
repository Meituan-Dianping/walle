package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.PayloadWriter;
import com.meituan.android.walle.utils.Fun1;

import java.io.File;
import java.util.List;


@Parameters(commandDescription = "remove channel info for apk")
public class RemoveChannelCommand implements IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", converter = FileConverter.class, variableArity = true)
    private List<File> files;

    @Parameter(names = {"-a", "--all"}, description = "remove all channel info")
    private boolean all = false;

    @Override
    public void parse() {
        if(all) {
            removeInfo(new Fun1<File, Boolean>() {
                @Override
                public Boolean apply(File file) {
                    return PayloadWriter.removeAll(file);
                }
            });
        }
    }
    private void removeInfo(Fun1<File, Boolean> fun) {
        for (File file : files) {
            System.out.println(file.getAbsolutePath() + " : " + fun.apply(file));
        }
    }
}

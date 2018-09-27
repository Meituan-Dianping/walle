package com.meituan.android.walle.commands;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.FileConverter;
import com.meituan.android.walle.ChannelWriter;
import com.meituan.android.walle.SignatureNotFoundException;
import com.meituan.android.walle.utils.Fun1;

import java.io.File;
import java.io.IOException;
import java.util.List;


@Parameters(commandDescription = "remove channel info for apk")
public class RemoveCommand implements IWalleCommand {

    @Parameter(required = true, description = "file1 file2 file3 ...", converter = FileConverter.class, variableArity = true)
    private List<File> files;

    @Override
    public void parse() {
        removeInfo(new Fun1<File, Boolean>() {
            @Override
            public Boolean apply(final File file) {
                try {
                    ChannelWriter.remove(file);
                    return true;
                } catch (IOException | SignatureNotFoundException e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }

    private void removeInfo(final Fun1<File, Boolean> fun) {
        for (File file : files) {
            System.out.println(file.getAbsolutePath() + " : " + fun.apply(file));
        }
    }
}

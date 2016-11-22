package com.meituan.android.walle;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.meituan.android.walle.commands.IWalleCommand;
import com.meituan.android.walle.commands.ShowCommand;
import com.meituan.android.walle.commands.RemoveCommand;
import com.meituan.android.walle.commands.WriteChannelCommand;
import com.meituan.android.walle.commands.WriteChannelsCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chentong on 20/11/2016.
 */

public class Main {
    public static void main(String[] args) throws Exception {
        Map<String, IWalleCommand> subCommandList = new HashMap<>();
        subCommandList.put("show", new ShowCommand());
        subCommandList.put("rm", new RemoveCommand());
        subCommandList.put("put", new WriteChannelCommand());
        subCommandList.put("batch", new WriteChannelsCommand());

        WalleCommandLine walleCommandLine = new WalleCommandLine();
        JCommander commander = new JCommander(walleCommandLine);

        for (Map.Entry<String, IWalleCommand> commandEntry : subCommandList.entrySet()) {
            commander.addCommand(commandEntry.getKey(), commandEntry.getValue());
        }
        try {
            commander.parse(args);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            commander.usage();
            System.exit(1);
            return;
        }

        walleCommandLine.parse(commander);

        String parseCommand = commander.getParsedCommand();
        if (parseCommand != null) {
            subCommandList.get(parseCommand).parse();
        }
    }
}

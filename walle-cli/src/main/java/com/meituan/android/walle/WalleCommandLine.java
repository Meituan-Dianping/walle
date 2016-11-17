package com.meituan.android.walle;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static sun.plugin.javascript.navig.JSType.Option;


public class WalleCommandLine {
    public static void main(String[] args) throws Exception {

        Options options = new Options();

        Option inputOption = new Option("i", "inputFile", true, "origin apk file");
        inputOption.setRequired(true);
        options.addOption(inputOption);

        Option outputOption = new Option("o", "outputFile", true, "output apk file");
        options.addOption(outputOption);

        Option channelOption = new Option("c", "channel", true, "single channel");
        inputOption.setRequired(true);
        options.addOption(channelOption);

        Option infoOption = new Option("e", "extraInfo", true, "extra info\n e.g.: -i time=1 type=android");
        infoOption.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(infoOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
            return;
        }

        String channel = cmd.getOptionValue("channel");

        String inputPath = cmd.getOptionValue("inputFile");
        File inputFile = new File(inputPath);

        String outputPath =cmd.getOptionValue("outputFile");
        File outputFile = null;
        if (outputPath == null) {
            String name = FilenameUtils.getBaseName(inputFile.getName());
            String extension = FilenameUtils.getExtension(inputFile.getName());
            String newName = name + "_" + channel + "." + extension;
            outputFile = new File(inputFile.getParent(), newName);
        } else {
            outputFile = new File(outputPath);
        }

        String[] extraInfo = cmd.getOptionValues("extraInfo");
        Map<String, String> extraInfoMap = null;
        if (extraInfo != null) {
            extraInfoMap = new HashMap<>();
            for (String s : extraInfo) {
                String[] keyValue = s.split("=");
                if (keyValue.length == 2) {
                    extraInfoMap.put(keyValue[0], keyValue[1]);
                }
            }
        }
        System.out.println(outputFile);
        FileUtils.copyFile(inputFile, outputFile);
        PayloadWriter.putChannel(outputFile, channel, extraInfoMap);
    }
}

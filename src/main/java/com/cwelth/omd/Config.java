package com.cwelth.omd;

import com.cwelth.omd.data.ThresholdItem;
import com.cwelth.omd.data.Thresholds;
import com.cwelth.omd.services.DonatePay;
import com.cwelth.omd.services.DonationAlerts;
import com.cwelth.omd.services.LocalFileWatcher;
import com.cwelth.omd.services.StreamLabs;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final String CATEGORY_MAIN = "main";

    public static final DonationAlerts DA = new DonationAlerts();
    public static final DonatePay DP = new DonatePay();
    public static final StreamLabs SL = new StreamLabs();
    public static final LocalFileWatcher LOCAL = new LocalFileWatcher();

    public static final String CATEGORY_THRESHOLDS = "thresholds";

    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    public static ForgeConfigSpec CLIENT_CONFIG;

    public static ForgeConfigSpec.ConfigValue<List<? extends List<String>>> THRESHOLDS_LIST;
    public static Thresholds THRESHOLDS_COLLECTION = new Thresholds();
    public static ForgeConfigSpec.ConfigValue<String> ECHOING;



    static {

        // Main Config
        CLIENT_BUILDER.comment("Main config").push(CATEGORY_MAIN);
        ECHOING = CLIENT_BUILDER.comment("Should the donation message appear in chat. \"after\" means that it will appear after command execution, \"before\" means that it will appear before command execution, \"off\" - no message at all.").defineInList("echoinchat", "after", Arrays.asList("off", "before", "after"));
        CLIENT_BUILDER.pop();

        // DonationAlerts.COM
        DA.init(CLIENT_BUILDER, "DonationAlerts.COM");

        //DonatePay.RU
        DP.init(CLIENT_BUILDER, "DonatePay.RU");

        //StreamLabs.COM
        SL.init(CLIENT_BUILDER, "StreamLabs.COM");

        //Local file
        LOCAL.init(CLIENT_BUILDER, "Local file (named \"otomd\" by default, located in root directory of modpack e.g mods\\..)");

        /*
        ACTIVATORS_LIST = CLIENT_BUILDER.comment("List of tunnel activators: item name postfix, tagret dimension, portal structure appearance block").defineList("activators", new ArrayList<>(Arrays.asList(
                new ArrayList<>(Arrays.asList("overworld", "minecraft:overworld", "minecraft:obsidian")),
                new ArrayList<>(Arrays.asList("nether", "minecraft:the_nether", "minecraft:netherrack")),
                new ArrayList<>(Arrays.asList("end", "minecraft:the_end", "minecraft:end_stone"))
        )), obj -> obj instanceof List);
        */

        CLIENT_BUILDER.comment("The list of donation thresholds in format of \"amount\", \"should be exact\", \"message for echoing\", \"command\"").push(CATEGORY_THRESHOLDS);

        THRESHOLDS_LIST = CLIENT_BUILDER.defineList("thresholds", new ArrayList<>(Arrays.asList(
                new ArrayList<>(Arrays.asList("100", "true", "Test donation from %name%, have a Diamond!", "/give @p minecraft:diamond")),
                new ArrayList<>(Arrays.asList("200", "false", "Test donation from %name%, %amount% coins, have a Block of Diamonds!", "/give @p minecraft:diamond_block")),
                new ArrayList<>(Arrays.asList("300", "true", "Test donation from %name%, %amount% coins, here's what he has to say: \"%message%\"", "/summon minecraft:blaze;/summon minecraft:blaze;/summon minecraft:blaze;/setblock ~ ~2 ~ minecraft:lava"))
        )), obj -> obj instanceof List);

        CLIENT_BUILDER.pop();
        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }

    public static void fillThresholdsCollection()
    {
        THRESHOLDS_COLLECTION.initFromConfig(THRESHOLDS_LIST);
    }


    public static void loadConfig(ForgeConfigSpec spec, Path path) {

        final CommentedFileConfig configData = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .preserveInsertionOrder()
                .build();

        configData.load();
        spec.setConfig(configData);
        fillThresholdsCollection();
    }

}

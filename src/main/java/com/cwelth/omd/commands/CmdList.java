package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.cwelth.omd.data.ThresholdItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.loading.FMLPaths;

public class CmdList {
    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("list")
                .executes( cs -> {
                    for(ThresholdItem item : Config.THRESHOLDS_COLLECTION.list)
                    {
                        cs.getSource().sendSuccess(new StringTextComponent("[OMD]" + TextFormatting.AQUA + " " + item.amount + ", exact: " + item.exact + ", message: " + item.message + ", commands: " + item.command), false);
                    }
                    return 0;
                });
    }
}

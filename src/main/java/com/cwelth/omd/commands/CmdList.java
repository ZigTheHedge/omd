package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.data.ThresholdItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class CmdList {
    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("list")
                .executes( cs -> {
                    for(ThresholdItem item : Config.THRESHOLDS_COLLECTION.list)
                    {
                        cs.getSource().sendSuccess(Component.translatable("[OMD]" + ChatFormatting.AQUA + " " + item.amount + ", exact: " + item.exact + ", message: " + item.message + ", commands: " + item.command), false);
                    }
                    return 0;
                });
    }
}

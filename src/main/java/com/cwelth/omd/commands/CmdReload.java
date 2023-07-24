package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

public class CmdReload {
    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("reload")
                .executes( cs -> {
                    Config.loadConfig(Config.CLIENT_CONFIG, FMLPaths.CONFIGDIR.get().resolve(OMD.MOD_ID + "-client.toml"));
                    Config.DP.stop();
                    Config.DA.stop();
                    Config.LOCAL.stop();
                    cs.getSource().sendSuccess(Component.translatable("[OMD]" + ChatFormatting.AQUA + " Config reloaded."), false);
                    return 0;
                });
    }
}

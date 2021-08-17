package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.loading.FMLPaths;

public class CmdReload {
    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("reload")
                .executes( cs -> {
                    Config.loadConfig(Config.CLIENT_CONFIG, FMLPaths.CONFIGDIR.get().resolve(OMD.MOD_ID + "-client.toml"));
                    Config.DP.stop();
                    Config.SL.stop();
                    Config.DA.stop();
                    Config.LOCAL.stop();
                    cs.getSource().sendSuccess(new StringTextComponent("[OMD]" + TextFormatting.AQUA + " Config reloaded."), false);
                    return 0;
                });
    }
}

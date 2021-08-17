package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.data.ThresholdItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.ComponentArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

public class CmdTest {
    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("test")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .then(Commands.argument("nickname", StringArgumentType.string())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes( cs -> {
                                int amount = IntegerArgumentType.getInteger(cs, "amount");
                                ThresholdItem matched = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                                if(matched == null)
                                {
                                    cs.getSource().sendFailure(new TranslationTextComponent("test.nomatch"));
                                    return 0;
                                }
                                String nickname = StringArgumentType.getString(cs, "nickname");
                                String message = StringArgumentType.getString(cs, "message");
                                cs.getSource().sendSuccess(new StringTextComponent("[OMD]" + TextFormatting.AQUA + " " + matched.getMessage(amount, nickname, message) + "\n" + matched.getCommand()), false);
                                if(cs.getSource().getEntity() instanceof ServerPlayerEntity)
                                {
                                    matched.runCommands(cs.getSource());
                                } else
                                {
                                    ClientPlayerEntity clientPlayer = (ClientPlayerEntity)cs.getSource().getEntity();
                                    matched.runCommands(clientPlayer);
                                }
                                return 0;
                            }))));
    }
}

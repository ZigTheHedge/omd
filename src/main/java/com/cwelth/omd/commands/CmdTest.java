package com.cwelth.omd.commands;

import com.cwelth.omd.Config;
import com.cwelth.omd.data.ThresholdItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CmdTest {
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("test")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .then(Commands.argument("nickname", StringArgumentType.string())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes( cs -> {
                                int amount = IntegerArgumentType.getInteger(cs, "amount");
                                ThresholdItem matched = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                                if(matched == null)
                                {
                                    cs.getSource().sendFailure(Component.translatable("test.nomatch"));
                                    return 0;
                                }
                                String nickname = StringArgumentType.getString(cs, "nickname");
                                String message = StringArgumentType.getString(cs, "message");
                                cs.getSource().sendSuccess(Component.translatable("[OMD]" + ChatFormatting.AQUA + " " + matched.getMessage(amount, nickname, message) + "\n" + matched.getCommand()), false);
                                if(cs.getSource().getEntity() instanceof ServerPlayer)
                                {
                                    matched.runCommands(cs.getSource());
                                } else
                                {
                                    LocalPlayer clientPlayer = (LocalPlayer) cs.getSource().getEntity();
                                    matched.runCommands(clientPlayer);
                                }
                                return 0;
                            }))));
    }
}

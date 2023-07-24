package com.cwelth.omd.data;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

public class ThresholdItem {
    public int amount;
    public boolean exact;
    public String message;
    public String command;

    public ThresholdItem(int amount, boolean exact, String message, String command)
    {
        this.amount = amount;
        this.exact = exact;
        this.message = message;
        this.command = command;
    }

    public String getMessage(int amount, String nickname, String message){
        String formattedMessage = this.message;
        formattedMessage = formattedMessage.replaceAll("%name%", nickname);
        formattedMessage = formattedMessage.replaceAll("%amount%", Integer.toString(amount));
        formattedMessage = formattedMessage.replaceAll("%message%", message);
        return formattedMessage;
    }

    public String getCommand() {
        return command;
    }

    public void runCommands(LocalPlayer player)
    {
        if(player == null) return;
        List<String> commands = Arrays.asList(getCommand().split(";"));
        for(String cmd : commands)
        {
            if(cmd.startsWith("/")) cmd = cmd.substring(1);
            player.commandUnsigned(cmd);
        }
    }

    public void runCommands(CommandSourceStack cs)
    {
        if(cs == null) return;
        List<String> commands = Arrays.asList(getCommand().split(";"));
        for(String cmd : commands)
        {
            ServerPlayer serverPlayer = (ServerPlayer) cs.getEntity();
            CommandDispatcher<CommandSourceStack> commanddispatcher = serverPlayer.getServer().getCommands().getDispatcher();
            if(cmd.startsWith("/")) cmd = cmd.substring(1);
            ParseResults<CommandSourceStack> results = commanddispatcher.parse(cmd, cs);

            serverPlayer.getServer().getCommands().performCommand(results, cmd);
        }
    }
}

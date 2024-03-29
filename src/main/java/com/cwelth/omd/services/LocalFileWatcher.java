package com.cwelth.omd.services;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.cwelth.omd.data.ThresholdItem;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class LocalFileWatcher extends DonationService {
    public LocalFileWatcher() {
        super("localfile");
    }

    @Override
    public void init(ForgeConfigSpec.Builder builder, String comment) {
        builder.comment(comment).push(CATEGORY);
        POLL_INTERVAL = builder.comment("Specifies polling interval for local file reads.").defineInRange("poll_interval", 5, 1, 60);
        OAUTH_KEY = builder.comment("Specifies the file name to watch for").define("oauth", "otomd");
        WEB_SOCKET = builder.comment("Not used for otomd").define("web_socket", false);
        RECONNECT_INTERVAL = builder.comment("Not used for otomd").defineInRange("reconnect_interval", 10, 5, 600);
        builder.pop();
    }

    @Override
    public boolean start(LocalPlayer player) {
        if(started) return true;
        if(OAUTH_KEY.get().isEmpty()) return false;
        started = true;
        this.player = player;

        this.player.sendSystemMessage(Component.translatable("service.start.success.rest", CATEGORY));
        return true;
    }

    @Override
    public void execute() {
        executorThread.submit( () -> {
            String filePath = getPath() + "/" + OAUTH_KEY.get();
            File otOMD = new File(filePath);
            if (otOMD.exists()) {
                List<String> allLines = null;
                try {
                    allLines = Files.readAllLines(Paths.get(filePath));
                } catch (IOException e) {
                    e.printStackTrace();
                    removeFile(otOMD);
                    return;
                }

                int amount = 0;
                try {
                    amount = Integer.parseInt(allLines.get(0));
                } catch (NumberFormatException nfe) {
                    nfe.printStackTrace();
                    OMD.LOGGER.error("otomd file structure is incorrect! Should contain LINES with: amount, nickname, message! AMOUNT is not integer");
                    removeFile(otOMD);
                    return;
                } catch (IndexOutOfBoundsException ioobe) {
                    ioobe.printStackTrace();
                    OMD.LOGGER.error("otomd file structure is incorrect! Should contain LINES with: amount, nickname, message! AMOUNT cannot be read");
                    removeFile(otOMD);
                    return;
                }

                String nickname = "";
                try {
                    nickname = allLines.get(1);
                } catch (IndexOutOfBoundsException ioobe) {
                    ioobe.printStackTrace();
                    OMD.LOGGER.error("otomd file structure is incorrect! Should contain LINES with: amount, nickname, message! NICKNAME cannot be read");
                    removeFile(otOMD);
                    return;
                }

                String message = "";
                try {
                    message = allLines.get(2);
                } catch (IndexOutOfBoundsException ioobe) {
                }

                ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                if (match != null) {
                    if (Config.ECHOING.get().equals("before"))
                        player.sendSystemMessage(Component.translatable(match.getMessage(amount, nickname, message)));
                    match.runCommands(player);
                    if (Config.ECHOING.get().equals("after"))
                        player.sendSystemMessage(Component.translatable(match.getMessage(amount, nickname, message)));
                }
                removeFile(otOMD);
            }
        });
    }

    public void removeFile(File file)
    {
        file.delete();
    }
}

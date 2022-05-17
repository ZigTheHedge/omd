package com.cwelth.omd.services;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.cwelth.omd.data.ThresholdItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;
import java.util.concurrent.Future;

public class DonatePay extends DonationService {
    public boolean revalidationNeeded = false;
    public DonatePay() {
        super("donatepay");
    }

    @Override
    public void init(ForgeConfigSpec.Builder builder, String comment)
    {
        builder.comment(comment).push(CATEGORY);
        OAUTH_KEY = builder.comment("OAUTH key used for authentication. Get yours from https://cwelth.com/omd/. Service integration disabled if blank.").define("oauth", "");
        WEB_SOCKET = builder.comment("Should WebSockets will be used as a communication protocol (preferred if the service supports it). Will fall back to REST-polling if false.").define("web_socket", false);
        POLL_INTERVAL = builder.comment("If WebSockets is disabled, specifies polling interval for REST requests (in seconds).").defineInRange("poll_interval", 45, 45, 600);
        RECONNECT_INTERVAL = builder.comment("If WebSockets is enabled and disconnect occurs, specifies time in seconds between reconnect attempts.").defineInRange("reconnect_interval", 10, 5, 600);
        builder.pop();
    }

    @Override
    public boolean start(ClientPlayerEntity player) {
        if(started) return true;
        if(Config.DP.OAUTH_KEY.get().isEmpty()) return false;
        started = true;
        this.player = player;
        OMD.LOGGER.info("[OMD] Starting DonatePay service...");
        if(Config.DP.WEB_SOCKET.get()) {
            player.sendMessage(new TranslationTextComponent("service.wss.notsupported", CATEGORY), Util.NIL_UUID);
            OMD.LOGGER.error("[OMD] DonatePay failed to start (WSS not supported).");
            return false;
        } else
        {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://donatepay.ru/api/v1/transactions?access_token=" + Config.DP.OAUTH_KEY.get() + "&limit=30", "GET", headers, "");
            if(response == null)
            {
                this.valid = false;
                player.sendMessage(new TranslationTextComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                OMD.LOGGER.error("[OMD] DonatePay failed to start (Connection issues).");
            } else {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                if(obj.get("status").getAsString().equals("error")) {
                    if(obj.get("message").getAsString().equals("Incorrect token")) {
                        this.valid = false;
                        player.sendMessage(new TranslationTextComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                        OMD.LOGGER.error("[OMD] DonatePay failed to start (Invalid token).");
                    } else {
                        this.revalidationNeeded = true;
                        this.valid = true;
                        ticksLeft = 20;
                        player.sendMessage(new TranslationTextComponent("service.start.failure.wait", CATEGORY), Util.NIL_UUID);
                        OMD.LOGGER.warn("[OMD] DonatePay start pending (Too many tries).");
                    }
                } else {
                    JsonArray dataElement = obj.get("data").getAsJsonArray();
                    if (dataElement.size() > 0)
                        lastDonationId = obj.get("data").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
                    this.valid = true;
                    player.sendMessage(new TranslationTextComponent("service.start.success.rest", CATEGORY), Util.NIL_UUID);
                    OMD.LOGGER.info("[OMD] DonatePay started successfully.");
                    ticksLeft = POLL_INTERVAL.get() * 20;
                }
            }
        }
        return false;
    }

    @Override
    public void execute() {
        if(!valid) return;
        ticksLeft = POLL_INTERVAL.get() * 20;
        executorThread.submit( () -> {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://donatepay.ru/api/v1/transactions?access_token=" + Config.DP.OAUTH_KEY.get() + "&limit=30", "GET", headers, "");

            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            if (obj.get("status").getAsString().equals("error"))
            {
                if(obj.get("message").getAsString().equals("Access once at 20 sec"))
                {
                    ticksLeft = 200;
                    return;
                }
            }
            if(this.revalidationNeeded)
            {
                this.revalidationNeeded = false;
                player.sendMessage(new TranslationTextComponent("service.start.success.rest", CATEGORY), Util.NIL_UUID);
                OMD.LOGGER.info("[OMD] DonatePay started successfully.");
                if (obj.get("data").getAsJsonArray().size() > 0)
                    lastDonationId = obj.get("data").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
                return;
            }
            JsonArray dataElement = obj.get("data").getAsJsonArray();
            if (dataElement.size() == 0) return;
            String lastId = dataElement.get(0).getAsJsonObject().get("id").getAsString();
            if (!lastId.equals(lastDonationId)) {
                int idx = 0;
                String last = lastId;
                while (!last.equals(lastDonationId)) {
                    try {
                        last = obj.get("data").getAsJsonArray().get(++idx).getAsJsonObject().get("id").getAsString();
                    } catch (IndexOutOfBoundsException e) {
                        idx = 0;
                        break;
                    }
                }
                if (lastDonationId == null) idx = 1;
                while (idx > 0) {
                    idx--;
                    JsonObject data = obj.get("data").getAsJsonArray().get(idx).getAsJsonObject();
                    int amount = (int) data.get("sum").getAsFloat();
                    String nickname = data.get("what").getAsString();
                    String msg = data.get("comment").getAsString();
                    String status = data.get("status").getAsString();
                    if(status.equals("success") || status.equals("user"))
                    {
                        ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                        String mText = "not found";
                        if(match != null) mText = match.getCommand();
                        OMD.LOGGER.info("[OMD] New DonatePay donation! " + nickname + ": " + amount + ", match: " + mText);
                        if (match != null) {
                            if (Config.ECHOING.get().equals("before"))
                                player.sendMessage(new StringTextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                            match.runCommands(player);
                            if (Config.ECHOING.get().equals("after"))
                                player.sendMessage(new StringTextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                        }
                        lastDonationId = data.get("id").getAsString();
                    }
                }
            }
        });
    }
}

package com.cwelth.omd.services;

import com.cwelth.omd.Config;
import com.cwelth.omd.data.ThresholdItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;

public class DonatePay extends DonationService {
    public DonatePay() {
        super("donatepay");
    }

    @Override
    public void init(ForgeConfigSpec.Builder builder, String comment)
    {
        builder.comment(comment).push(CATEGORY);
        OAUTH_KEY = builder.comment("OAUTH key used for authentication. Get yours from https://cwelth.com/omd/. Service integration disabled if blank.").define("oauth", "");
        WEB_SOCKET = builder.comment("Should WebSockets will be used as a communication protocol (preferred if the service supports it). Will fall back to REST-polling if false.").define("web_socket", false);
        POLL_INTERVAL = builder.comment("If WebSockets is disabled, specifies polling interval for REST requests (in seconds).").defineInRange("poll_interval", 20, 20, 60);
        RECONNECT_INTERVAL = builder.comment("If WebSockets is enabled and disconnect occurs, specifies time in seconds between reconnect attempts.").defineInRange("reconnect_interval", 10, 5, 600);
        builder.pop();
    }

    @Override
    public boolean start(ClientPlayerEntity player) {
        if(started) return true;
        if(Config.DP.OAUTH_KEY.get().isEmpty()) return false;
        started = true;
        this.player = player;

        if(Config.DP.WEB_SOCKET.get()) {
            player.sendMessage(new TranslationTextComponent("service.wss.notsupported", CATEGORY), Util.NIL_UUID);
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
            } else {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                if(obj.get("status").getAsString().equals("error")) {
                    this.valid = false;
                    player.sendMessage(new TranslationTextComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                } else {
                    JsonArray dataElement = obj.get("data").getAsJsonArray();
                    if (dataElement.size() > 0)
                        lastDonationId = obj.get("data").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
                    this.valid = true;
                    player.sendMessage(new TranslationTextComponent("service.start.success.rest", CATEGORY), Util.NIL_UUID);
                    ticksLeft = POLL_INTERVAL.get() * 20;
                }
            }
        }
        return false;
    }

    @Override
    public void execute() {
        if(!valid) return;
        executorThread.submit( () -> {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://donatepay.ru/api/v1/transactions?access_token=" + Config.DP.OAUTH_KEY.get() + "&limit=30", "GET", headers, "");

            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            if (obj.get("status").getAsString().equals("error")) return;
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
                    ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
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
        });
    }
}
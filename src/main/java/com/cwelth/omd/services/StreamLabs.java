package com.cwelth.omd.services;

import com.cwelth.omd.Config;
import com.cwelth.omd.data.ThresholdItem;
import com.cwelth.omd.websocket.WebSocketEndpoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamLabs extends DonationService {

    public StreamLabs() {
        super("streamlabs");
    }

    @Override
    public boolean start(LocalPlayer player) {
        if(started) return true;
        if(Config.SL.OAUTH_KEY.get().isEmpty()) return false;
        started = true;
        this.player = player;

        if(Config.SL.WEB_SOCKET.get()) {
            /*
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://streamlabs.com/api/v1.0/socket/token?access_token=" + OAUTH_KEY.get(), "GET", headers, "");
            if(response == null) {
                this.valid = false;
                player.sendMessage(new TranslationTextComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                return false;
            }
            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            wssToken = obj.get("socket_token").getAsString();

            player.sendMessage(new TranslationTextComponent("service.start.success.wss", CATEGORY), Util.NIL_UUID);

            new Thread(() -> {
                try {
                    if(websocket == null) {
                        websocket = new WebSocketEndpoint(new URI("wss://sockets.streamlabs.com?token=" + wssToken), player, this, CATEGORY);
                        websocket.addMessageHandler(new WebSocketEndpoint.MessageHandler() {
                            @Override
                            public void handleMessage(String message) {
                                JsonObject obj = new JsonParser().parse(message).getAsJsonObject();

                                if (obj.has("type")) {
                                    if (obj.get("type").getAsString() == "donation") {

                                        JsonObject data = obj.get("message").getAsJsonArray().get(0).getAsJsonObject();
                                        int amount = (int)data.get("amount").getAsFloat();
                                        String nickname = data.get("name").getAsString();
                                        String msg = data.get("message").getAsString();
                                        ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                                        if (match != null) {
                                            if (Config.ECHOING.get().equals("before"))
                                                player.sendMessage(new StringTextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                                            player.chat(match.getCommand());
                                            if (Config.ECHOING.get().equals("after"))
                                                player.sendMessage(new StringTextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                                        }
                                    }
                                }
                            }
                        });

                        this.valid = true;
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }).start();
            */
            player.sendMessage(new TranslatableComponent("service.wss.notsupported", CATEGORY), Util.NIL_UUID);
            return false;

        } else
        {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://streamlabs.com/api/v1.0/donations?access_token=" + OAUTH_KEY.get() + "&limit=30", "GET", headers, "");
            if(response == null)
            {
                this.valid = false;
                player.sendMessage(new TranslatableComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
            } else {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                if(obj.has("error")) {
                    this.valid = false;
                    player.sendMessage(new TranslatableComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                } else {
                    JsonArray dataElement = obj.get("data").getAsJsonArray();
                    if (dataElement.size() > 0)
                        lastDonationId = obj.get("data").getAsJsonArray().get(0).getAsJsonObject().get("donation_id").getAsString();
                    this.valid = true;
                    player.sendMessage(new TranslatableComponent("service.start.success.rest", CATEGORY), Util.NIL_UUID);
                    ticksLeft = POLL_INTERVAL.get() * 20;
                }
            }
        }
        return true;
    }

    @Override
    public void execute() {
        if(!valid) return;
        executorThread.submit( () -> {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            String response = performSyncJSONRequest("https://streamlabs.com/api/v1.0/donations?access_token=" + OAUTH_KEY.get() + "&limit=30", "GET", headers, "");

            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            if (obj.has("error")) return;
            JsonArray dataElement = obj.get("data").getAsJsonArray();
            if (dataElement.size() == 0) return;
            String lastId = dataElement.get(0).getAsJsonObject().get("donation_id").getAsString();
            if (!lastId.equals(lastDonationId)) {
                int idx = 0;
                String last = lastId;
                while (!last.equals(lastDonationId)) {
                    try {
                        last = obj.get("data").getAsJsonArray().get(++idx).getAsJsonObject().get("donation_id").getAsString();
                    } catch (IndexOutOfBoundsException e) {
                        idx = 0;
                        break;
                    }
                }
                if (lastDonationId == null) idx = 1;
                while (idx > 0) {
                    idx--;
                    JsonObject data = obj.get("data").getAsJsonArray().get(idx).getAsJsonObject();
                    int amount = (int) data.get("amount").getAsFloat();
                    String nickname = data.get("name").getAsString();
                    String msg = data.get("message").getAsString();
                    ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                    if (match != null) {
                        if (Config.ECHOING.get().equals("before"))
                            player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                        match.runCommands(player);
                        if (Config.ECHOING.get().equals("after"))
                            player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                    }
                    lastDonationId = data.get("donation_id").getAsString();
                }
            }
        });
    }
}

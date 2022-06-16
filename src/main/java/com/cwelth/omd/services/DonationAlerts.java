package com.cwelth.omd.services;

import com.cwelth.omd.Config;
import com.cwelth.omd.OMD;
import com.cwelth.omd.data.ThresholdItem;
import com.cwelth.omd.websocket.WebSocketEndpoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.net.*;
import java.util.HashMap;

public class DonationAlerts extends DonationService {

    public DonationAlerts() {
        super("donationalerts");
    }

    @Override
    public void init(ForgeConfigSpec.Builder builder, String comment)
    {
        builder.comment(comment).push(CATEGORY);
        OAUTH_KEY = builder.comment("OAUTH key used for authentication. Get yours from https://cwelth.com/omd/. Service integration disabled if blank.").define("oauth", "");
        WEB_SOCKET = builder.comment("Should WebSockets will be used as a communication protocol (preferred if the service supports it). Will fall back to REST-polling if false.").define("web_socket", true);
        POLL_INTERVAL = builder.comment("If WebSockets is disabled, specifies polling interval for REST requests (in seconds).").defineInRange("poll_interval", 5, 5, 60);
        RECONNECT_INTERVAL = builder.comment("If WebSockets is enabled and disconnect occurs, specifies time in seconds between reconnect attempts.").defineInRange("reconnect_interval", 10, 5, 600);
        builder.pop();
    }

    @Override
    public boolean start(LocalPlayer player) {
        if(started) return true;
        if(Config.DA.OAUTH_KEY.get().isEmpty()) return false;
        started = true;
        this.player = player;
        wssState = EnumWssState.START;
        OMD.LOGGER.info("[OMD] Starting DonationAlerts service...");
        if(Config.DA.WEB_SOCKET.get()) {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + OAUTH_KEY.get());
            String response = performSyncJSONRequest("https://www.donationalerts.com/api/v1/user/oauth", "GET", headers, "");
            //TODO: Error handling
            if(response == null)
            {
                this.valid = false;
                player.sendMessage(new TranslatableComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
                OMD.LOGGER.error("[OMD] DonationAlerts failed to start (Connection issues).");
                return false;
            }

            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            wssToken = obj.get("data").getAsJsonObject().get("socket_connection_token").getAsString();
            serviceUserID = obj.get("data").getAsJsonObject().get("id").getAsString();

            new Thread(() -> {
                try {
                    if(websocket == null) {
                        websocket = new WebSocketEndpoint(new URI("wss://centrifugo.donationalerts.com/connection/websocket"), player, this, CATEGORY);
                        websocket.addMessageHandler(new WebSocketEndpoint.MessageHandler() {
                            @Override
                            public void handleMessage(String message) {
                                JsonObject obj = new JsonParser().parse(message).getAsJsonObject();

                                if (obj.has("id")) {
                                    if (obj.get("id").getAsInt() == 1) {
                                        wssClientId = obj.get("result").getAsJsonObject().get("client").getAsString();
                                        wssState = EnumWssState.WAITFORCLIENTID;
                                        //player.sendMessage(new StringTextComponent("Handshake succeeded! Got Client-ID: " + wssClientId), Util.NIL_UUID);

                                        HashMap<String, String> headers = new HashMap<>();
                                        headers.put("Content-Type", "application/json; utf-8");
                                        headers.put("Accept", "application/json");
                                        headers.put("Authorization", "Bearer " + OAUTH_KEY.get());
                                        String response = performSyncJSONRequest("https://www.donationalerts.com/api/v1/centrifuge/subscribe", "POST", headers, "{\"channels\":[\"$alerts:donation_" + serviceUserID + "\"], \"client\":\"" + wssClientId + "\"}");

                                        obj = new JsonParser().parse(response).getAsJsonObject();
                                        wssChannelToken = obj.get("channels").getAsJsonArray().get(0).getAsJsonObject().get("token").getAsString();
                                        wssState = EnumWssState.WAITFORCHANNELID;
                                        player.sendMessage(new TranslatableComponent("service.start.success.wss", CATEGORY), Util.NIL_UUID);
                                        OMD.LOGGER.info("[OMD] DonationAlerts started successfully.");

                                    } else if (obj.get("id").getAsInt() == 2) {
                                        //System.out.println("Got channel_id: " + message);
                                        wssState = EnumWssState.READY;
                                    }
                                } else if (obj.has("result")) {
                                    if (!obj.get("result").getAsJsonObject().has("type")) {
                                        JsonObject data = obj.get("result").getAsJsonObject().get("data").getAsJsonObject().get("data").getAsJsonObject();
                                        int amount = (int)data.get("amount_in_user_currency").getAsDouble();
                                        String nickname = data.get("username").getAsString();
                                        String msg = data.get("message").getAsString();
                                        ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                                        String mText = "not found";
                                        if(match != null) mText = match.getCommand();
                                        OMD.LOGGER.info("[OMD] New DonationAlerts donation! " + nickname + ": " + amount + ", match: " + mText);
                                        if (match != null) {
                                            if (Config.ECHOING.get().equals("before"))
                                                player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                                            match.runCommands(player);
                                            if (Config.ECHOING.get().equals("after"))
                                                player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                                        }
                                    }
                                } else
                                    System.out.println("Unknown message received! Report it to mod author please: " + message);
                            }
                        });
                        executorThread.submit( () -> {
                            while (true) {
                                switch (wssState) {
                                    case START: {
                                        // System.out.println("Sending handshake. ");
                                        websocket.sendMessage("{\"params\": {\"token\": \"" + wssToken + "\"}, \"id\": 1}");
                                        break;
                                    }
                                    case WAITFORCHANNELID: {
                                        // System.out.println("Sending request for channel_id. ");
                                        websocket.sendMessage("{ \"params\": { \"channel\": \"$alerts:donation_" + serviceUserID + "\", \"token\": \"" + wssChannelToken + "\" }, \"method\": 1, \"id\": 2 }");
                                        break;
                                    }
                                }
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if(!started)
                                {
                                    websocket.disconnect();
                                    break;
                                }
                            }
                        });

                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }).start();

        } else {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + OAUTH_KEY.get());
            String response = performSyncJSONRequest("https://www.donationalerts.com/api/v1/alerts/donations", "GET", headers, "");
            if(response == null)
            {
                this.valid = false;
                player.sendMessage(new TranslatableComponent("service.start.failure", CATEGORY, "Check your OAUTH key!"), Util.NIL_UUID);
            } else {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                JsonArray dataElement = obj.get("data").getAsJsonArray();
                if(dataElement.size() > 0)
                    lastDonationId = obj.get("data").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
                this.valid = true;
                player.sendMessage(new TranslatableComponent("service.start.success.rest", CATEGORY), Util.NIL_UUID);
                ticksLeft = POLL_INTERVAL.get() * 20;
            }
        }
        return true;
    }

    @Override
    public void execute() {
        if(!valid) return;
        executorThread.submit( () -> {
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json; utf-8");
            headers.put("Accept", "application/json");
            headers.put("Authorization", "Bearer " + OAUTH_KEY.get());
            String response = performSyncJSONRequest("https://www.donationalerts.com/api/v1/alerts/donations", "GET", headers, "");

            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
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
                    int amount = data.get("amount_in_user_currency").getAsInt();
                    String nickname = data.get("username").getAsString();
                    String msg = data.get("message").getAsString();
                    ThresholdItem match = Config.THRESHOLDS_COLLECTION.getSuitableThreshold(amount);
                    if (match != null) {
                        if (Config.ECHOING.get().equals("before"))
                            player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                        match.runCommands(player);
                        if (Config.ECHOING.get().equals("after"))
                            player.sendMessage(new TextComponent(match.getMessage(amount, nickname, msg)), Util.NIL_UUID);
                    }
                    lastDonationId = data.get("id").getAsString();
                }
            }
        });
    }
}

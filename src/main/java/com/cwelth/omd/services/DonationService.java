package com.cwelth.omd.services;

import com.cwelth.omd.OMD;
import com.neovisionaries.ws.client.WebSocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class DonationService {
    public String CATEGORY;
    public ForgeConfigSpec.ConfigValue<String> OAUTH_KEY;
    public ForgeConfigSpec.BooleanValue WEB_SOCKET;
    public ForgeConfigSpec.IntValue POLL_INTERVAL;
    public ForgeConfigSpec.IntValue RECONNECT_INTERVAL;
    public boolean started = false;
    public boolean valid = false;
    public LocalPlayer player;
    public int ticksLeft;
    public WebSocket websocket = null;
    public String wssClientId = "";
    public String wssToken = "";
    public String wssChannelToken = "";
    public String serviceUserID = "";
    public EnumWssState wssState = EnumWssState.START;
    public String lastDonationId = null;
    public boolean shouldKeepTrying = false;

    public ExecutorService executorThread = Executors.newFixedThreadPool(1);

    public DonationService(String category_name){
        CATEGORY = category_name;
    }

    public void init(ForgeConfigSpec.Builder builder, String comment)
    {
        builder.comment(comment).push(CATEGORY);
        OAUTH_KEY = builder.comment("OAUTH key used for authentication. Get yours from https://cwelth.com/omd/. Service integration disabled if blank.").define("oauth", "");
        WEB_SOCKET = builder.comment("Should WebSockets will be used as a communication protocol (preferred if the service supports it). Will fall back to REST-polling if false.").define("web_socket", false);
        POLL_INTERVAL = builder.comment("If WebSockets is disabled, specifies polling interval for REST requests (in seconds).").defineInRange("poll_interval", 5, 5, 60);
        RECONNECT_INTERVAL = builder.comment("If WebSockets is enabled and disconnect occurs, specifies time in seconds between reconnect attempts.").defineInRange("reconnect_interval", 10, 5, 600);
        builder.pop();
    }

    public abstract boolean start(LocalPlayer player);

    public void stop()
    {
        started = false;
        valid = false;
        websocket = null;
    }

    public abstract void execute();

    public void tick()
    {
        if(!started)return;
        if(WEB_SOCKET.get())return;
        if(ticksLeft > 0)
        {
            ticksLeft--;
        }
        if(ticksLeft == 0)
        {
            ticksLeft = 5 * 20; //POLL_INTERVAL.get() * 20;
            execute();
        }
    }

    public static String getPath() {
        final File file = Minecraft.getInstance().gameDirectory;
        try {
            return file.getCanonicalFile().getPath();
        } catch (final IOException e) {
            OMD.LOGGER.warn("Could not canonize path!");
        }
        return file.getPath();
    }

    public String performSyncJSONRequest(String uri, String method, HashMap<String, String> headers, String body)
    {
        try {
            URL url = new URL(uri);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            for(Map.Entry<String, String> header: headers.entrySet())
                con.setRequestProperty(header.getKey(), header.getValue());
            con.setDoOutput(true);
            if(body != "") {
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = body.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
            }
            int code = con.getResponseCode();
            /*
            if(code >= 400) {
                return null;
            }

             */
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return response.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setState(EnumWssState newState)
    {
        wssState = newState;
    }
}

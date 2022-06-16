package com.cwelth.omd.websocket;

import com.cwelth.omd.services.DonationAlerts;
import com.cwelth.omd.services.DonationService;
import com.cwelth.omd.services.EnumWssState;
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TranslatableComponent;


import jakarta.websocket.*;
import java.io.IOException;
import java.net.URI;

@ClientEndpoint
public class WebSocketEndpoint {
    Session userSession = null;
    private MessageHandler messageHandler;
    private LocalPlayer player;
    private String serviceName;
    private boolean shouldKeepTrying = false;
    private WebSocketContainer container;
    private URI endpoint;
    private DonationService serviceClass;

    public WebSocketEndpoint(URI endpointURI, LocalPlayer player, DonationService serviceClass, String serviceName) {
        this.player = player;
        this.serviceClass = serviceClass;
        this.serviceName = serviceName;
        try {
            container = ContainerProvider.getWebSocketContainer();
            endpoint = endpointURI;
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect()
    {
        try {
            userSession.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        this.userSession = userSession;
        if(shouldKeepTrying) {
            player.sendMessage(new TranslatableComponent("service.websocket.reconnected", serviceName), Util.NIL_UUID);
            shouldKeepTrying = false;
            serviceClass.wssState = EnumWssState.START;
        }
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        System.out.println("closing websocket. " + reason.getReasonPhrase());
        player.sendMessage(new TranslatableComponent("service.websocket.disconnect", serviceName, reason.getReasonPhrase()), Util.NIL_UUID);
        this.userSession = null;
        shouldKeepTrying = true;
        new Thread( () -> {
            while(shouldKeepTrying) {
                try {
                    player.sendMessage(new TranslatableComponent("service.websocket.tryreconnect", serviceName), Util.NIL_UUID);
                    container.connectToServer(this, endpoint);
                } catch (DeploymentException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(serviceClass.RECONNECT_INTERVAL.get() * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {

        public void handleMessage(String message);
    }
}

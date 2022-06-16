package com.cwelth.omd;

import com.cwelth.omd.commands.CmdList;
import com.cwelth.omd.commands.CmdReload;
import com.cwelth.omd.commands.CmdTest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(OMD.MOD_ID)
public class OMD
{
    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "omd";

    public OMD() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        Config.loadConfig(Config.CLIENT_CONFIG, FMLPaths.CONFIGDIR.get().resolve(MOD_ID + "-client.toml"));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandlers());
    }

    public class ForgeEventHandlers {

        @SubscribeEvent
        public void registerCommands(RegisterCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            LiteralCommandNode<CommandSourceStack> cmdsOMD = dispatcher.register(
                    Commands.literal("omd")
                            .then(CmdTest.register(dispatcher))
                            .then(CmdReload.register(dispatcher))
                            .then(CmdList.register(dispatcher))
            );
        }

        @SubscribeEvent
        public void clientTick(TickEvent.PlayerTickEvent event)
        {
            if (event.phase != TickEvent.Phase.START && event.side == LogicalSide.CLIENT)
            {
                if(event.player instanceof RemotePlayer) return;
                Config.DA.start((LocalPlayer)event.player);
                Config.DP.start((LocalPlayer)event.player);
                Config.SL.start((LocalPlayer)event.player);
                Config.LOCAL.start((LocalPlayer)event.player);
                Config.DP.tick();
                Config.SL.tick();
                Config.DA.tick();
                Config.LOCAL.tick();
            }
        }

        @SubscribeEvent
        public void serverStopping(ServerStoppingEvent event)
        {
            Config.DP.stop();
            Config.SL.stop();
            Config.DA.stop();
            Config.LOCAL.stop();
        }

    }

}
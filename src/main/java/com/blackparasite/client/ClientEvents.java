package com.blackparasite.client;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.client.gui.HomeScreen;
import com.blackparasite.client.gui.PowerWheelScreen;
import com.blackparasite.client.model.ParasiteRenderer;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.network.ModNetwork;
import com.blackparasite.network.packet.AbilityPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/** Événements côté client (bus FORGE) : touches, rendu des joueurs parasités, bouton du menu principal. */
@Mod.EventBusSubscriber(modid = BlackParasiteMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ClientState.tick();
        if (mc.player == null || mc.level == null) return;

        // Particules ambiantes des porteurs proches
        for (AbstractClientPlayer p : mc.level.players()) {
            ClientBridge.Visual v = ClientBridge.visual(p.getUUID());
            if (v != null && !(p == mc.player && mc.options.getCameraType().isFirstPerson())) ParasiteRenderer.tickParticles(p, v);
        }
        if (mc.screen != null) return;

        ParasiteData d = ClientState.DATA;
        while (Keys.OPEN_MENU.consumeClick()) mc.setScreen(new HomeScreen(null));
        while (Keys.WHEEL.consumeClick()) if (d.infected) mc.setScreen(new PowerWheelScreen());
        while (Keys.CYCLE.consumeClick()) if (d.infected) cycleAbility(d);
        while (Keys.USE_ABILITY.consumeClick()) if (d.infected) sendAbility(ClientState.selectedAbility);
        while (Keys.ABSORB.consumeClick()) if (d.infected) sendAbility(ParasiteAbility.ABSORB.ordinal());
    }

    public static void sendAbility(int ordinal) {
        ModNetwork.CHANNEL.sendToServer(new AbilityPacket(ordinal));
    }

    private static void cycleAbility(ParasiteData d) {
        ParasiteAbility[] all = ParasiteAbility.values();
        var form = d.currentForm();
        for (int i = 1; i <= all.length; i++) {
            ParasiteAbility a = all[(ClientState.selectedAbility + i) % all.length];
            if (a != ParasiteAbility.ABSORB && d.level >= a.minLevel && a.allowedIn(form)) {
                ClientState.selectedAbility = a.ordinal();
                ClientState.pushAlert(com.blackparasite.network.packet.NotifyPacket.INFO, a.displayName, "Capacité sélectionnée (touche V)");
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        ClientBridge.Visual v = ClientBridge.visual(p.getUUID());
        if (v == null) return;
        if (ParasiteRenderer.renderPlayer(p, v, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTitleInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            event.addListener(Button.builder(Component.literal("THE BLACK PARASITE"),
                    b -> Minecraft.getInstance().setScreen(new HomeScreen(event.getScreen())))
                    .bounds(8, 8, 130, 20).build());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }
}

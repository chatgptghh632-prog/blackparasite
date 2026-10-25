package com.blackparasite.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.network.packet.AnimPacket;

import net.minecraft.util.Mth;

/**
 * État côté client : copie en lecture seule des données du joueur local (envoyée par le serveur),
 * animations en cours, alertes du HUD et valeurs lissées pour l'affichage.
 */
public final class ClientState {

    public static final ParasiteData DATA = new ParasiteData();

    /** Capacité choisie pour la touche « utiliser ». */
    public static int selectedAbility = ParasiteAbility.CLAWS.ordinal();

    /** Compteur de ticks client (indépendant du temps du monde). */
    public static long clientTick = 0;

    // ---- valeurs lissées du HUD ----
    public static float shownEnergy = 100f;
    public static float shownHunger = 100f;
    public static float shownHealth = 20f;
    public static float shownXp = 0f;
    /** Éclat de la barre d'évolution après un gain (ticks restants). */
    public static int progressFlash = 0;
    public static long lastGainTick = -1000;

    // ---- alertes ----
    public record Alert(int kind, String title, String subtitle, long start, int duration) {}

    public static final Deque<Alert> TOASTS = new ArrayDeque<>();
    public static Alert bigAlert = null;

    // ---- animations ponctuelles ----
    public record Anim(int id, long start) {}

    public static final Map<UUID, Anim> ANIMS = new HashMap<>();

    private ClientState() {}

    public static void pushAlert(int kind, String title, String subtitle) {
        if (kind == com.blackparasite.network.packet.NotifyPacket.EVOLUTION) {
            bigAlert = new Alert(kind, title, subtitle, clientTick, 150);
            return;
        }
        if (kind == com.blackparasite.network.packet.NotifyPacket.GAIN) {
            progressFlash = 24;
            lastGainTick = clientTick;
        }
        TOASTS.addLast(new Alert(kind, title, subtitle, clientTick, 90));
        while (TOASTS.size() > 5) TOASTS.pollFirst();
    }

    public static void startAnim(UUID player, int id) {
        ANIMS.put(player, new Anim(id, clientTick));
    }

    /** Durée en ticks de chaque animation (sert aussi à la progression 0..1 du modèle). */
    public static int animDuration(int id) {
        if (id == AnimPacket.TRANSFORM) return 40;
        if (id == AnimPacket.EVOLVE) return 60;
        ParasiteAbility a = ParasiteAbility.byOrdinal(id);
        if (a == null) return 20;
        return switch (a) {
            case ABSORB -> 20;
            case CLAWS -> 12;
            case WHIP -> 14;
            case LEAP -> 24;
            case ROAR -> 36;
            case CARAPACE -> 16;
            case VISION -> 20;
            case REGEN -> 24;
            case CAMOUFLAGE -> 16;
            case BURST -> 28;
            case ULTIMATE -> 50;
        };
    }

    /** Appelé une fois par tick client. */
    public static void tick() {
        clientTick++;
        ParasiteData d = DATA;
        for (int i = 0; i < d.cooldownRemaining.length; i++) {
            if (d.cooldownRemaining[i] > 0) d.cooldownRemaining[i]--;
        }
        if (d.carapaceTicks > 0) d.carapaceTicks--;
        if (d.camoTicks > 0) d.camoTicks--;
        if (d.regenTicks > 0) d.regenTicks--;
        if (d.visionTicks > 0) d.visionTicks--;
        if (progressFlash > 0) progressFlash--;

        shownEnergy = Mth.lerp(0.2f, shownEnergy, d.energy);
        shownHunger = Mth.lerp(0.2f, shownHunger, d.hunger);
        float target = d.xpNeeded <= 0 ? 1f : Math.min(1f, d.xpInto / (float) d.xpNeeded);
        // Si le niveau vient de changer la cible retombe : la barre se vide d'un coup puis se remplit.
        shownXp = target < shownXp - 0.5f ? 0f : Mth.lerp(0.08f, shownXp, target);

        if (bigAlert != null && clientTick - bigAlert.start() > bigAlert.duration()) bigAlert = null;
        while (!TOASTS.isEmpty() && clientTick - TOASTS.peekFirst().start() > TOASTS.peekFirst().duration()) TOASTS.pollFirst();
        ANIMS.values().removeIf(a -> clientTick - a.start() > animDuration(a.id()) + 2);
    }

    /** Réinitialise tout à la déconnexion. */
    public static void reset() {
        DATA.reset();
        ANIMS.clear();
        TOASTS.clear();
        bigAlert = null;
        progressFlash = 0;
        ClientBridge.clear();
    }
}

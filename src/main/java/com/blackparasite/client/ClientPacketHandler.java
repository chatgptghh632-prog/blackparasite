package com.blackparasite.client;

import com.blackparasite.ModSounds;
import com.blackparasite.network.packet.AnimPacket;
import com.blackparasite.network.packet.NotifyPacket;
import com.blackparasite.network.packet.SyncVisualPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;

/** Réception côté client des paquets du serveur (appelé uniquement via DistExecutor.unsafeRunWhenOn(CLIENT)). */
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    public static void handleSync(CompoundTag tag) {
        ClientState.DATA.readSync(tag);
        // Valeurs affichées : on part des valeurs réelles à la première synchronisation.
        if (ClientState.clientTick < 5 || ClientState.shownEnergy <= 0f && ClientState.DATA.energy > 0f) {
            ClientState.shownEnergy = ClientState.DATA.energy;
            ClientState.shownHunger = ClientState.DATA.hunger;
        }
    }

    public static void handleVisual(SyncVisualPacket msg) {
        ClientBridge.set(msg.player, new ClientBridge.Visual(msg.form, msg.stage, msg.camouflage, msg.carapace));
    }

    public static void handleNotify(NotifyPacket msg) {
        ClientState.pushAlert(msg.kind, msg.title, msg.subtitle);
        if (msg.kind == NotifyPacket.EVOLUTION) play(ModSounds.EVOLVE.get(), 1.0f, 1.0f);
        else if (msg.kind == NotifyPacket.GAIN) play(ModSounds.ABSORB.get(), 0.5f, 1.4f);
        else if (msg.kind == NotifyPacket.WARNING) play(ModSounds.HEARTBEAT.get(), 0.6f, 1.0f);
    }

    public static void handleAnim(AnimPacket msg) {
        ClientState.startAnim(msg.player, msg.anim);
    }

    public static void play(SoundEvent event, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}

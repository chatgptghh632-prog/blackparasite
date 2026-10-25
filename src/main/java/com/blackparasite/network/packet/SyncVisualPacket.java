package com.blackparasite.network.packet;

import java.util.UUID;
import java.util.function.Supplier;

import com.blackparasite.client.ClientPacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Serveur → clients qui suivent le joueur : ce qu'il faut pour dessiner son parasite. */
public class SyncVisualPacket {
    public final UUID player;
    /** Ordinal de la forme, -1 = aucune. */
    public final int form;
    /** Stade d'évolution visuelle 0..10 (niveau / 10). */
    public final int stage;
    public final boolean camouflage;
    public final boolean carapace;

    public SyncVisualPacket(UUID player, int form, int stage, boolean camouflage, boolean carapace) {
        this.player = player;
        this.form = form;
        this.stage = stage;
        this.camouflage = camouflage;
        this.carapace = carapace;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeVarInt(form + 1);
        buf.writeVarInt(stage);
        buf.writeBoolean(camouflage);
        buf.writeBoolean(carapace);
    }

    public static SyncVisualPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int form = buf.readVarInt() - 1;
        int stage = buf.readVarInt();
        boolean camo = buf.readBoolean();
        boolean carapace = buf.readBoolean();
        return new SyncVisualPacket(id, form, stage, camo, carapace);
    }

    public static void handle(SyncVisualPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleVisual(msg)));
        ctx.get().setPacketHandled(true);
    }
}

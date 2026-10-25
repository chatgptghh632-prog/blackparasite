package com.blackparasite.network.packet;

import java.util.function.Supplier;

import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.logic.ParasiteService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client → serveur : demande de changement de forme (la forme doit être débloquée). */
public class TransformPacket {
    public final int form;

    public TransformPacket(int form) {
        this.form = form;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(form);
    }

    public static TransformPacket decode(FriendlyByteBuf buf) {
        return new TransformPacket(buf.readVarInt());
    }

    public static void handle(TransformPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            ParasiteForm form = ParasiteForm.byOrdinal(msg.form);
            if (player != null && form != null) ParasiteService.requestTransform(player, form);
        });
        context.setPacketHandled(true);
    }
}

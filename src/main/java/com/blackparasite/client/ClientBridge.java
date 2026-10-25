package com.blackparasite.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.blackparasite.logic.ParasiteForm;

import net.minecraft.world.entity.player.Player;

/**
 * État visuel des parasites vus par ce client (le sien et ceux des joueurs proches), reçu par SyncVisualPacket.
 * Cette classe n'importe AUCUNE classe réservée au client : le code commun (ServerEvents) peut l'appeler sans risque
 * côté client uniquement ({@code level.isClientSide}).
 */
public final class ClientBridge {

    /** @param form ordinal de ParasiteForm ; @param stage 0..10 */
    public record Visual(int form, int stage, boolean camouflage, boolean carapace) {
        @Nullable
        public ParasiteForm parasiteForm() {
            return ParasiteForm.byOrdinal(form);
        }
    }

    private static final Map<UUID, Visual> VISUALS = new ConcurrentHashMap<>();

    private ClientBridge() {}

    public static void set(UUID id, Visual visual) {
        if (visual.form() < 0) VISUALS.remove(id);
        else VISUALS.put(id, visual);
    }

    @Nullable
    public static Visual visual(UUID id) {
        return VISUALS.get(id);
    }

    public static void clear() {
        VISUALS.clear();
    }

    public static double jumpMultiplier(Player player) {
        Visual v = VISUALS.get(player.getUUID());
        ParasiteForm form = v == null ? null : v.parasiteForm();
        return form == null ? 1.0 : form.jumpMultiplier;
    }
}

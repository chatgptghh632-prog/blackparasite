package com.blackparasite.data;

import java.util.LinkedHashSet;
import java.util.Set;

import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.logic.Progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Données du parasite attachées à un joueur.
 * <ul>
 *   <li>Champs « persistants » : écrits par {@link #save()} dans les données du joueur (survivent à la
 *       déconnexion, au redémarrage, à la mort grâce à PlayerEvent.Clone).</li>
 *   <li>Champs « transitoires » : timers de capacités, jamais sauvegardés ; envoyés au client par
 *       {@link #writeSync()} pour l'affichage.</li>
 * </ul>
 * Le serveur est la seule autorité : le client reçoit une copie en lecture seule.
 */
public class ParasiteData {
    // ---- persistant ----
    public boolean infected;
    /** Ordinal de la forme active, -1 si non infecté. */
    public int form = -1;
    public int unlockedMask;
    public long experience;
    public int level = 1;
    /** Biomasse disponible (ressource dépensable). */
    public int biomass;
    /** Biomasse totale jamais absorbée (statistique). */
    public long totalBiomass;
    public float hunger = 100f;
    public float energy = 100f;
    public int absorptions;
    public int categoryMask;
    public int mutantsDefeated;
    public int abilityUses;
    public int missionMask;
    public int loreMask;
    public final Set<String> species = new LinkedHashSet<>();

    // ---- transitoire (serveur) ----
    public final long[] cooldownEnd = new long[ParasiteAbility.values().length];
    public int carapaceTicks;
    public int camoTicks;
    public int regenTicks;
    public int visionTicks;
    public int leapGraceTicks;
    public long transformCooldownEnd;
    public boolean dirty = true;
    public int lastSentHunger = -1;
    public int lastSentEnergy = -1;
    public int lastVisualHash = Integer.MIN_VALUE;

    // ---- transitoire (client, remplis par readSync) ----
    public long xpInto;
    public long xpNeeded;
    public final int[] cooldownRemaining = new int[ParasiteAbility.values().length];

    // ------------------------------------------------------------------ accès
    public boolean hasForm(ParasiteForm f) {
        return (unlockedMask & f.mask()) != 0;
    }

    public ParasiteForm currentForm() {
        return infected ? ParasiteForm.byOrdinal(form) : null;
    }

    public float maxEnergy() {
        return Progression.maxEnergy(level);
    }

    public void recalcLevel() {
        this.level = Progression.levelFor(experience);
    }

    // ------------------------------------------------------------------ persistance
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Infected", infected);
        tag.putInt("Form", form);
        tag.putInt("Unlocked", unlockedMask);
        tag.putLong("Xp", experience);
        tag.putInt("Level", level);
        tag.putInt("Biomass", biomass);
        tag.putLong("TotalBiomass", totalBiomass);
        tag.putFloat("Hunger", hunger);
        tag.putFloat("Energy", energy);
        tag.putInt("Absorptions", absorptions);
        tag.putInt("Categories", categoryMask);
        tag.putInt("Mutants", mutantsDefeated);
        tag.putInt("Uses", abilityUses);
        tag.putInt("Missions", missionMask);
        tag.putInt("Lore", loreMask);
        ListTag list = new ListTag();
        for (String s : species) list.add(StringTag.valueOf(s));
        tag.put("Species", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        infected = tag.getBoolean("Infected");
        form = tag.contains("Form") ? tag.getInt("Form") : -1;
        unlockedMask = tag.getInt("Unlocked");
        experience = Math.max(0L, tag.getLong("Xp"));
        // Le niveau est lu tel quel : le serveur le recalcule via recalcLevel() après chargement,
        // le client ne recalcule jamais (sa configuration peut différer de celle du serveur).
        level = Math.max(1, tag.getInt("Level"));
        biomass = Math.max(0, tag.getInt("Biomass"));
        totalBiomass = Math.max(0L, tag.getLong("TotalBiomass"));
        hunger = tag.contains("Hunger") ? Math.max(0f, Math.min(100f, tag.getFloat("Hunger"))) : 100f;
        energy = tag.contains("Energy") ? Math.max(0f, tag.getFloat("Energy")) : 100f;
        absorptions = tag.getInt("Absorptions");
        categoryMask = tag.getInt("Categories");
        mutantsDefeated = tag.getInt("Mutants");
        abilityUses = tag.getInt("Uses");
        missionMask = tag.getInt("Missions");
        loreMask = tag.getInt("Lore");
        species.clear();
        ListTag list = tag.getList("Species", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) species.add(list.getString(i));
        // Cohérence : forme invalide → on repart de la forme de base.
        if (infected && ParasiteForm.byOrdinal(form) == null) form = ParasiteForm.PORTEUR.ordinal();
        if (infected) unlockedMask |= ParasiteForm.PORTEUR.mask();
        energy = Math.min(energy, maxEnergy());
        dirty = true;
    }

    /** Copie les champs persistants (clone après mort ou changement de dimension). */
    public void copyPersistentFrom(ParasiteData other) {
        load(other.save());
        recalcLevel(); // serveur uniquement
    }

    /** Remise à zéro complète (commande de reset, bouton de configuration). */
    public void reset() {
        load(new CompoundTag());
        for (int i = 0; i < cooldownEnd.length; i++) cooldownEnd[i] = 0L;
        carapaceTicks = camoTicks = regenTicks = visionTicks = leapGraceTicks = 0;
        transformCooldownEnd = 0L;
        lastVisualHash = Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ synchronisation
    /** Données envoyées au client propriétaire : persistant + valeurs calculées + recharges restantes. */
    public CompoundTag writeSync(long gameTime, Progression.LevelInfo info) {
        CompoundTag tag = save();
        tag.putLong("XpInto", info.into());
        tag.putLong("XpNeeded", info.needed());
        int[] cd = new int[cooldownEnd.length];
        for (int i = 0; i < cd.length; i++) cd[i] = (int) Math.max(0L, cooldownEnd[i] - gameTime);
        tag.putIntArray("Cd", cd);
        tag.putInt("Carapace", carapaceTicks);
        tag.putInt("Camo", camoTicks);
        tag.putInt("Regen", regenTicks);
        tag.putInt("Vision", visionTicks);
        return tag;
    }

    public void readSync(CompoundTag tag) {
        load(tag);
        xpInto = tag.getLong("XpInto");
        xpNeeded = tag.getLong("XpNeeded");
        int[] cd = tag.getIntArray("Cd");
        for (int i = 0; i < cooldownRemaining.length; i++) cooldownRemaining[i] = i < cd.length ? cd[i] : 0;
        carapaceTicks = tag.getInt("Carapace");
        camoTicks = tag.getInt("Camo");
        regenTicks = tag.getInt("Regen");
        visionTicks = tag.getInt("Vision");
    }
}

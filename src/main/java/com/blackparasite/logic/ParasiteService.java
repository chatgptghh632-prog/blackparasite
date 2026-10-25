package com.blackparasite.logic;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.blackparasite.ModSounds;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteCapability;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.network.ModNetwork;
import com.blackparasite.network.packet.AnimPacket;
import com.blackparasite.network.packet.NotifyPacket;
import com.blackparasite.network.packet.SyncDataPacket;
import com.blackparasite.network.packet.SyncVisualPacket;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Logique serveur du parasite : infection, expérience, formes, besoins, synchronisation.
 * Rien ici n'est exécuté côté client.
 */
public final class ParasiteService {

    public static final String MUTANT_TAG = "bp_mutant";

    private static final UUID HEALTH_ID = UUID.fromString("5b1c3a10-6f0e-4a0b-9c11-0b1a2c3d4e01");
    private static final UUID SPEED_ID = UUID.fromString("5b1c3a10-6f0e-4a0b-9c11-0b1a2c3d4e02");
    private static final UUID ATTACK_ID = UUID.fromString("5b1c3a10-6f0e-4a0b-9c11-0b1a2c3d4e03");
    private static final UUID KNOCKBACK_ID = UUID.fromString("5b1c3a10-6f0e-4a0b-9c11-0b1a2c3d4e04");

    /** Vrai pendant qu'une capacité inflige ses dégâts : évite d'appliquer deux fois le multiplicateur. */
    public static boolean suppressDamageHook = false;

    private ParasiteService() {}

    // ------------------------------------------------------------------ accès
    @Nullable
    public static ParasiteData get(Player player) {
        return player.getCapability(ParasiteCapability.PARASITE).resolve().orElse(null);
    }

    public static boolean isInfected(Player player) {
        ParasiteData d = get(player);
        return d != null && d.infected;
    }

    public static float hungerFactor(ParasiteData d) {
        if (d.hunger <= 0f) return 0.5f;
        if (d.hunger < 25f) return 0.75f;
        return 1f;
    }

    /** Multiplicateur des dégâts infligés par le porteur. */
    public static double powerMultiplier(ParasiteData d) {
        ParasiteForm form = d.currentForm();
        if (form == null) return 1.0;
        return form.damageMultiplier * Progression.levelPower(d.level) * hungerFactor(d);
    }

    /** Multiplicateur des dégâts subis par le porteur (≤ 1). */
    public static double incomingMultiplier(ParasiteData d) {
        ParasiteForm form = d.currentForm();
        if (form == null) return 1.0;
        double reduction = Math.min(0.75, form.resistance + Progression.levelResistance(d.level));
        double mult = 1.0 - reduction;
        if (d.carapaceTicks > 0) mult *= 0.6;
        return mult;
    }

    // ------------------------------------------------------------------ infection / réinitialisation
    public static boolean infect(ServerPlayer player) {
        ParasiteData d = get(player);
        if (d == null || d.infected) return false;
        d.infected = true;
        d.form = ParasiteForm.PORTEUR.ordinal();
        d.unlockedMask |= ParasiteForm.PORTEUR.mask();
        d.hunger = 100f;
        d.energy = d.maxEnergy();
        refreshAttributes(player);
        ServerLevel level = player.serverLevel();
        burst(level, dust(), player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.2);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
        notify(player, NotifyPacket.EVOLUTION, "INFECTION DÉTECTÉE", ParasiteForm.PORTEUR.displayName);
        broadcastAnim(player, AnimPacket.TRANSFORM);
        checkProgress(player, d);
        syncData(player);
        syncVisual(player, true);
        return true;
    }

    public static void resetPlayer(ServerPlayer player) {
        ParasiteData d = get(player);
        if (d == null) return;
        endCamouflage(player, d);
        d.reset();
        removeAttributes(player);
        syncData(player);
        syncVisual(player, true);
    }

    // ------------------------------------------------------------------ expérience, formes, missions
    public static void addExperience(ServerPlayer player, ParasiteData d, long amount) {
        if (amount <= 0 || !d.infected) return;
        int before = d.level;
        d.experience += amount;
        d.recalcLevel();
        d.dirty = true;
        if (d.level > before) onLevelUp(player, d, before);
        else checkProgress(player, d);
    }

    private static void onLevelUp(ServerPlayer player, ParasiteData d, int before) {
        refreshAttributes(player);
        boolean newStage = before / 10 != d.level / 10;
        if (newStage) {
            notify(player, NotifyPacket.EVOLUTION, "ÉVOLUTION DÉTECTÉE", "Stade d'évolution " + (d.level / 10));
            broadcastAnim(player, AnimPacket.EVOLVE);
            syncVisual(player, true);
        } else {
            notify(player, NotifyPacket.GAIN, "Niveau " + d.level, "");
        }
        for (ParasiteForm f : ParasiteForm.values()) {
            // L'Abomination ne se débloque jamais par le niveau seul : il faut la capacité ultime.
            if (f == ParasiteForm.ABOMINATION) continue;
            if (d.level >= f.unlockLevel && !d.hasForm(f)) unlockForm(player, d, f);
        }
        checkProgress(player, d);
    }

    public static void unlockForm(ServerPlayer player, ParasiteData d, ParasiteForm form) {
        if (d.hasForm(form)) return;
        d.unlockedMask |= form.mask();
        d.dirty = true;
        ServerLevel level = player.serverLevel();
        notify(player, NotifyPacket.EVOLUTION, "ÉVOLUTION DÉTECTÉE", form.displayName);
        broadcastAnim(player, AnimPacket.EVOLVE);
        burst(level, ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.6, 0.08);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 0.7f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.EVOLVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        checkProgress(player, d);
    }

    /** Vérifie les missions et les entrées de journal ; accorde les récompenses une seule fois. */
    public static void checkProgress(ServerPlayer player, ParasiteData d) {
        if (!d.infected) return;
        for (Lore lore : Lore.values()) {
            if ((d.loreMask & lore.mask()) == 0 && lore.isUnlockedBy(d)) {
                d.loreMask |= lore.mask();
                d.dirty = true;
                notify(player, NotifyPacket.INFO, "Journal : " + lore.title, "Nouvelle entrée dans l'écran Évolutions");
            }
        }
        for (Mission m : Mission.values()) {
            if ((d.missionMask & m.mask()) == 0 && m.isDone(d)) {
                d.missionMask |= m.mask(); // marqué avant la récompense : pas de boucle possible
                d.biomass += m.reward;
                d.dirty = true;
                notify(player, NotifyPacket.MISSION, "Mission accomplie : " + m.title, "+" + m.reward + " biomasse et expérience");
                addExperience(player, d, m.reward);
            }
        }
    }

    // ------------------------------------------------------------------ transformation
    public static void requestTransform(ServerPlayer player, ParasiteForm target) {
        ParasiteData d = get(player);
        if (d == null || !d.infected || !player.isAlive() || player.isSpectator()) return;
        if (!d.hasForm(target) || d.form == target.ordinal()) return;
        long now = player.level().getGameTime();
        if (now < d.transformCooldownEnd) return;
        if (d.energy < 10f) {
            notify(player, NotifyPacket.WARNING, "Énergie insuffisante", "La transformation coûte 10 énergie");
            return;
        }
        d.energy -= 10f;
        applyForm(player, d, target);
    }

    /** Applique une forme (sans vérifier les conditions : l'appelant s'en charge). */
    public static void applyForm(ServerPlayer player, ParasiteData d, ParasiteForm target) {
        d.form = target.ordinal();
        d.transformCooldownEnd = player.level().getGameTime() + 60;
        endCamouflage(player, d);
        d.carapaceTicks = 0;
        d.regenTicks = 0;
        ServerLevel level = player.serverLevel();
        // Arrêt bref (3/4 de seconde), jamais une cinématique bloquante.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 4, false, false, false));
        burst(level, dust(), player.getX(), player.getY() + 1.0, player.getZ(), 45, 0.6, 0.15);
        burst(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.5, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 0.8f);
        broadcastAnim(player, AnimPacket.TRANSFORM);
        refreshAttributes(player);
        d.dirty = true;
        syncData(player);
        syncVisual(player, true);
    }

    // ------------------------------------------------------------------ attributs
    public static void refreshAttributes(ServerPlayer player) {
        removeAttributes(player);
        ParasiteData d = get(player);
        if (d == null || !d.infected) return;
        ParasiteForm form = d.currentForm();
        if (form == null) return;

        double speed = form.speedBonus + Progression.levelSpeedBonus(d.level);
        if (d.hunger <= 0f) speed -= 0.1;

        add(player, Attributes.MAX_HEALTH, HEALTH_ID, "Black Parasite health",
                form.healthBonus + Progression.levelHealthBonus(d.level), AttributeModifier.Operation.ADDITION);
        add(player, Attributes.MOVEMENT_SPEED, SPEED_ID, "Black Parasite speed", speed,
                AttributeModifier.Operation.MULTIPLY_BASE);
        add(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, "Black Parasite attack", form.attackBonus,
                AttributeModifier.Operation.ADDITION);
        if (form.knockbackResistance > 0) {
            add(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "Black Parasite knockback",
                    form.knockbackResistance, AttributeModifier.Operation.ADDITION);
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void add(ServerPlayer p, Attribute attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null && amount != 0.0) inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    public static void removeAttributes(ServerPlayer p) {
        remove(p, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(p, Attributes.ATTACK_DAMAGE, ATTACK_ID);
        remove(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void remove(ServerPlayer p, Attribute attr, UUID id) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // ------------------------------------------------------------------ tick serveur
    public static void tick(ServerPlayer player) {
        ParasiteData d = get(player);
        if (d == null || !d.infected) return;
        ParasiteForm form = d.currentForm();
        if (form == null || !player.isAlive()) return;

        float hungerBefore = d.hunger;
        d.hunger = (float) Math.max(0.0, d.hunger - ParasiteConfig.COMMON.hungerDrainPerSecond.get() / 20.0);
        hungerAlerts(player, hungerBefore, d.hunger);
        if (hungerBefore > 0f && d.hunger <= 0f) refreshAttributes(player);

        double regen = form.energyRegen / 20.0;
        if (d.hunger <= 0f) regen = 0.0;
        else if (d.hunger < 25f) regen *= 0.5;
        d.energy = Math.min(d.maxEnergy(), d.energy + (float) regen);

        if (d.carapaceTicks > 0) d.carapaceTicks--;
        if (d.visionTicks > 0) d.visionTicks--;
        if (d.leapGraceTicks > 0) d.leapGraceTicks--;
        if (d.camoTicks > 0 && --d.camoTicks == 0) endCamouflage(player, d);
        if (d.regenTicks > 0) {
            d.regenTicks--;
            if (d.regenTicks % 20 == 0) {
                if (d.biomass >= 2) {
                    d.biomass -= 2;
                    player.heal(2.0f);
                    burst(player.serverLevel(), ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.02);
                    d.dirty = true;
                } else {
                    d.regenTicks = 0;
                    notify(player, NotifyPacket.WARNING, "Biomasse épuisée", "La régénération s'arrête");
                }
            }
        }

        if (player.tickCount % 20 == 0) ambient(player, d);

        if (player.tickCount % 10 == 0) {
            int h = Math.round(d.hunger);
            int e = Math.round(d.energy);
            if (d.dirty || h != d.lastSentHunger || e != d.lastSentEnergy) {
                d.lastSentHunger = h;
                d.lastSentEnergy = e;
                syncData(player);
            }
            syncVisual(player, false);
        }
    }

    private static void hungerAlerts(ServerPlayer p, float before, float after) {
        if (before >= 25f && after < 25f) {
            notify(p, NotifyPacket.WARNING, "Le parasite a faim", "Absorbez une créature ou mangez de la viande crue");
        } else if (before >= 10f && after < 10f) {
            notify(p, NotifyPacket.WARNING, "Faim critique", "Vos capacités s'affaiblissent");
        } else if (before > 0f && after <= 0f) {
            notify(p, NotifyPacket.WARNING, "Le parasite est affamé", "État affaibli : dégâts réduits, plus de régénération d'énergie");
        }
    }

    /** Manifestations ambiantes rares + marquage visuel des créatures mutantes proches. */
    private static void ambient(ServerPlayer player, ParasiteData d) {
        ServerLevel level = player.serverLevel();
        double freq = ParasiteConfig.COMMON.eventFrequency.get();
        if (freq > 0.0) {
            boolean night = level.isNight();
            double chance = (night ? 0.04 : 0.008) * freq; // par seconde
            if (level.random.nextDouble() < chance) {
                double ox = (level.random.nextDouble() - 0.5) * 10.0;
                double oz = (level.random.nextDouble() - 0.5) * 10.0;
                burst(level, dust(), player.getX() + ox, player.getY() + 0.3, player.getZ() + oz, 14, 0.8, 0.02);
                if (night && level.random.nextFloat() < 0.5f) {
                    level.playSound(null, player.getX() + ox, player.getY(), player.getZ() + oz,
                            ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.5f, 0.9f);
                }
            }
        }
        if (ParasiteConfig.COMMON.mutantChance.get() > 0.0) {
            List<Monster> mutants = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(24.0),
                    m -> m.isAlive() && m.getPersistentData().getBoolean(MUTANT_TAG));
            for (Monster m : mutants) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
                burst(level, ParticleTypes.SQUID_INK, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 4, 0.3, 0.02);
            }
        }
    }

    public static void endCamouflage(ServerPlayer player, ParasiteData d) {
        if (d.camoTicks > 0) {
            d.camoTicks = 0;
            d.dirty = true;
        }
        player.removeEffect(MobEffects.INVISIBILITY);
    }

    // ------------------------------------------------------------------ dégâts de capacité
    public static boolean dealAbilityDamage(ServerPlayer player, LivingEntity target, float amount) {
        suppressDamageHook = true;
        try {
            return target.hurt(player.damageSources().playerAttack(player), amount);
        } finally {
            suppressDamageHook = false;
        }
    }

    // ------------------------------------------------------------------ réseau
    public static void syncData(ServerPlayer player) {
        ParasiteData d = get(player);
        if (d == null) return;
        d.dirty = false;
        Progression.LevelInfo info = Progression.info(d.experience);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncDataPacket(d.writeSync(player.level().getGameTime(), info)));
    }

    public static void syncVisual(ServerPlayer player, boolean force) {
        ParasiteData d = get(player);
        if (d == null) return;
        SyncVisualPacket pkt = visualPacket(player, d);
        int hash = Objects.hash(pkt.form, pkt.stage, pkt.camouflage, pkt.carapace);
        if (!force && hash == d.lastVisualHash) return;
        d.lastVisualHash = hash;
        if (ParasiteConfig.COMMON.multiplayerVisualSync.get()) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), pkt);
        } else {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        }
    }

    /** Envoie à {@code viewer} l'apparence de {@code target} (quand il commence à le suivre). */
    public static void sendVisualTo(ServerPlayer target, ServerPlayer viewer) {
        if (!ParasiteConfig.COMMON.multiplayerVisualSync.get()) return;
        ParasiteData d = get(target);
        if (d == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), visualPacket(target, d));
    }

    private static SyncVisualPacket visualPacket(ServerPlayer player, ParasiteData d) {
        int form = d.infected ? d.form : -1;
        return new SyncVisualPacket(player.getUUID(), form, Math.min(10, d.level / 10),
                d.camoTicks > 0, d.carapaceTicks > 0);
    }

    public static void notify(ServerPlayer player, int kind, String title, String subtitle) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NotifyPacket(kind, title, subtitle));
    }

    public static void broadcastAnim(ServerPlayer player, int anim) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new AnimPacket(player.getUUID(), anim));
    }

    // ------------------------------------------------------------------ particules / sons
    public static ParticleOptions dust() {
        return new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.06f), 1.4f);
    }

    /** Envoie des particules en respectant le plafond de configuration. */
    public static void burst(ServerLevel level, ParticleOptions type, double x, double y, double z,
                             int count, double spread, double speed) {
        int capped = Math.min(count, ParasiteConfig.COMMON.maxParticlesPerEffect.get());
        level.sendParticles(type, x, y, z, capped, spread, spread, spread, speed);
    }

    public static void playAt(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}

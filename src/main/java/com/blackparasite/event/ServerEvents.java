package com.blackparasite.event;

import java.util.Set;
import java.util.UUID;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteCapability;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.BiomassTable;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.logic.ParasiteService;
import com.blackparasite.network.packet.NotifyPacket;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Événements Forge côté serveur (bus FORGE). Tout est exécuté uniquement sur le serveur logique. */
@Mod.EventBusSubscriber(modid = BlackParasiteMod.MODID)
public final class ServerEvents {

    private static final UUID MUTANT_HEALTH = UUID.fromString("7c2d4b20-1a3e-4b5c-8d6f-0a1b2c3d4e51");
    private static final UUID MUTANT_DAMAGE = UUID.fromString("7c2d4b20-1a3e-4b5c-8d6f-0a1b2c3d4e52");

    private static final Set<Item> RAW_MEAT = Set.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN,
            Items.RABBIT, Items.COD, Items.SALMON, Items.ROTTEN_FLESH);

    private ServerEvents() {}

    // ------------------------------------------------------------------ capability
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            ParasiteCapability.Provider provider = new ParasiteCapability.Provider();
            event.addCapability(ParasiteCapability.KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    /** Conserve la progression à la mort et lors du retour de l'End. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        event.getOriginal().reviveCaps();
        ParasiteData oldData = ParasiteService.get(event.getOriginal());
        ParasiteData newData = ParasiteService.get(event.getEntity());
        if (oldData != null && newData != null) {
            newData.copyPersistentFrom(oldData);
            if (event.isWasDeath() && newData.infected) {
                // Les timers (carapace, camouflage, régénération, recharges) repartent de zéro : ils ne sont pas copiés.
                newData.energy = Math.min(newData.energy, newData.maxEnergy() * 0.5f);
                newData.hunger = Math.max(newData.hunger, 40f);
                if (!ParasiteConfig.COMMON.keepFormOnDeath.get()) {
                    newData.form = ParasiteForm.PORTEUR.ordinal();
                }
            }
        }
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    private static void resync(ServerPlayer sp) {
        ParasiteData d = ParasiteService.get(sp);
        if (d != null) {
            d.lastVisualHash = Integer.MIN_VALUE;
            ParasiteService.refreshAttributes(sp);
        }
        ParasiteService.syncData(sp);
        ParasiteService.syncVisual(sp, true);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            ParasiteService.sendVisualTo(target, viewer);
        }
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player instanceof ServerPlayer sp) {
            ParasiteService.tick(sp);
        }
    }

    // ------------------------------------------------------------------ combat
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        // Dégâts subis par un porteur : résistance de forme/niveau + carapace.
        if (victim instanceof ServerPlayer sp) {
            ParasiteData d = ParasiteService.get(sp);
            if (d != null && d.infected) event.setAmount((float) (event.getAmount() * ParasiteService.incomingMultiplier(d)));
        }

        // Dégâts infligés par un porteur (hors capacités, qui appliquent déjà leur multiplicateur).
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            ParasiteData d = ParasiteService.get(attacker);
            if (d != null && d.infected) {
                if (!ParasiteService.suppressDamageHook) {
                    event.setAmount((float) (event.getAmount() * ParasiteService.powerMultiplier(d)));
                }
                // Attaquer brise le camouflage.
                if (d.camoTicks > 0) {
                    ParasiteService.endCamouflage(attacker, d);
                    ParasiteService.syncVisual(attacker, true);
                    ParasiteService.syncData(attacker);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        ParasiteData d = ParasiteService.get(killer);
        if (d == null || !d.infected || !BiomassTable.isAbsorbable(victim)) return;

        boolean mutant = victim.getPersistentData().getBoolean(ParasiteService.MUTANT_TAG);
        double ratio = ParasiteConfig.COMMON.killXpRatio.get();
        long xp = Math.round(BiomassTable.entryFor(victim).biomass() * (mutant ? 3.0 : 1.0) * ratio);
        if (mutant) d.mutantsDefeated++;
        if (xp > 0 || mutant) {
            d.dirty = true;
            if (xp > 0) ParasiteService.addExperience(killer, d, xp);
            else ParasiteService.checkProgress(killer, d);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ParasiteData d = ParasiteService.get(sp);
        if (d == null || !d.infected) return;
        ParasiteForm form = d.currentForm();
        if (d.leapGraceTicks > 0) {
            event.setDamageMultiplier(0.0f); // le bond prédateur ne blesse pas à l'atterrissage
            d.leapGraceTicks = 0;
        }
        // Impact au sol du Colosse / de l'Abomination.
        if ((form == ParasiteForm.COLOSSE || form == ParasiteForm.ABOMINATION) && event.getDistance() > 3.0f) {
            ServerLevel level = sp.serverLevel();
            float damage = Math.min(20f, event.getDistance()) * 0.8f * (float) ParasiteService.powerMultiplier(d);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(4.0),
                    e -> e != sp && !(e instanceof Player) && e.isAlive())) {
                ParasiteService.dealAbilityDamage(sp, e, damage);
                e.knockback(0.8, sp.getX() - e.getX(), sp.getZ() - e.getZ());
            }
            ParasiteService.burst(level, ParticleTypes.POOF, sp.getX(), sp.getY() + 0.1, sp.getZ(), 24, 1.2, 0.05);
            ParasiteService.playAt(level, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GENERIC_EXPLODE, 0.6f, 0.6f);
            event.setDamageMultiplier(Math.min(event.getDamageMultiplier(), 0.5f));
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        // Exécuté côté serveur ET côté client : la saute est prédite par le client.
        if (!(event.getEntity() instanceof Player player)) return;
        double mult = jumpMultiplierFor(player);
        if (mult > 1.0) {
            var m = player.getDeltaMovement();
            player.setDeltaMovement(m.x, m.y + 0.42 * (mult - 1.0), m.z);
        }
    }

    private static double jumpMultiplierFor(Player player) {
        if (player.level().isClientSide) {
            return com.blackparasite.client.ClientBridge.jumpMultiplier(player);
        }
        ParasiteData d = ParasiteService.get(player);
        ParasiteForm form = d == null ? null : d.currentForm();
        return form == null ? 1.0 : form.jumpMultiplier;
    }

    // ------------------------------------------------------------------ camouflage
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getNewTarget() instanceof ServerPlayer sp) {
            ParasiteData d = ParasiteService.get(sp);
            if (d != null && d.infected && d.camoTicks > 0 && event.getEntity().distanceToSqr(sp) > 16.0) {
                // Camouflage : les créatures à plus de 4 blocs ne vous prennent pas pour cible.
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------ nourriture
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ParasiteData d = ParasiteService.get(sp);
        if (d == null || !d.infected || !RAW_MEAT.contains(event.getItem().getItem())) return;
        d.hunger = Math.min(100f, d.hunger + 12f);
        d.dirty = true;
    }

    // ------------------------------------------------------------------ créatures mutantes
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof Monster monster) || !(event.getLevel() instanceof ServerLevel level)) return;
        double chance = ParasiteConfig.COMMON.mutantChance.get();
        if (chance <= 0.0 || level.random.nextDouble() >= chance) return;
        if (monster.getPersistentData().getBoolean(ParasiteService.MUTANT_TAG)) return;

        boolean carrierNearby = false;
        for (Player p : level.players()) {
            if (p.distanceToSqr(monster) < 64.0 * 64.0 && ParasiteService.isInfected(p)) {
                carrierNearby = true;
                break;
            }
        }
        if (!carrierNearby) return;

        monster.getPersistentData().putBoolean(ParasiteService.MUTANT_TAG, true);
        monster.setCustomName(Component.literal("Créature parasitée"));
        monster.setCustomNameVisible(false);
        AttributeInstance health = monster.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addPermanentModifier(new AttributeModifier(MUTANT_HEALTH, "Black Parasite mutant health", 1.0,
                    AttributeModifier.Operation.MULTIPLY_BASE));
            monster.setHealth(monster.getMaxHealth());
        }
        AttributeInstance attack = monster.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.addPermanentModifier(new AttributeModifier(MUTANT_DAMAGE, "Black Parasite mutant damage", 0.5,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        for (Player p : level.players()) {
            if (p instanceof ServerPlayer sp && sp.distanceToSqr(monster) < 64.0 * 64.0 && ParasiteService.isInfected(sp)) {
                ParasiteService.notify(sp, NotifyPacket.INFO, "Une présence parasitaire", "Une créature a muté près de vous");
            }
        }
    }
}

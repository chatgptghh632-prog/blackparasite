package com.blackparasite.logic;

import java.util.List;

import javax.annotation.Nullable;

import com.blackparasite.ModSounds;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.network.packet.NotifyPacket;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Exécution serveur des capacités. Chaque appel est entièrement revalidé : forme, niveau, configuration,
 * recharge et énergie. Le client ne fait que demander.
 */
public final class AbilityHandler {

    private AbilityHandler() {}

    public static void use(ServerPlayer player, ParasiteAbility ability) {
        ParasiteData d = ParasiteService.get(player);
        if (d == null || !d.infected || !player.isAlive() || player.isSpectator()) return;
        if (!ParasiteConfig.COMMON.abilityEnabled.get(ability).get()) {
            warn(player, "Capacité désactivée", "Le serveur a désactivé « " + ability.displayName + " »");
            return;
        }
        ParasiteForm form = d.currentForm();
        if (!ability.allowedIn(form)) {
            warn(player, "Indisponible dans cette forme", ability.displayName);
            return;
        }
        if (d.level < ability.minLevel) {
            warn(player, "Niveau " + ability.minLevel + " requis", ability.displayName);
            return;
        }
        long now = player.level().getGameTime();
        if (now < d.cooldownEnd[ability.ordinal()]) return; // en recharge : on ignore silencieusement
        float cost = (float) (ability.energyCost * ParasiteConfig.COMMON.energyCostMultiplier.get());
        if (d.energy < cost) {
            warn(player, "Énergie insuffisante", ability.displayName + " coûte " + Math.round(cost) + " énergie");
            return;
        }

        if (!execute(player, d, ability, form)) return;

        d.energy = Math.max(0f, d.energy - cost);
        d.cooldownEnd[ability.ordinal()] = now + ability.cooldownTicks;
        d.abilityUses++;
        d.dirty = true;
        ParasiteService.broadcastAnim(player, ability.ordinal());
        ParasiteService.checkProgress(player, d);
        ParasiteService.syncData(player);
    }

    private static boolean execute(ServerPlayer p, ParasiteData d, ParasiteAbility ability, ParasiteForm form) {
        return switch (ability) {
            case ABSORB -> absorb(p, d);
            case CLAWS -> claws(p, d, form);
            case WHIP -> whip(p, d);
            case LEAP -> leap(p, d);
            case ROAR -> roar(p, d);
            case CARAPACE -> carapace(p, d);
            case VISION -> vision(p, d);
            case REGEN -> regen(p, d);
            case CAMOUFLAGE -> camouflage(p, d);
            case BURST -> burst(p, d);
            case ULTIMATE -> ultimate(p, d);
        };
    }

    // ------------------------------------------------------------------ ciblage
    private static void warn(ServerPlayer p, String title, String sub) {
        ParasiteService.notify(p, NotifyPacket.WARNING, title, sub);
    }

    private static boolean validTarget(ServerPlayer p, Entity e) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        if (e instanceof Player) {
            if (!ParasiteConfig.COMMON.abilitiesAffectPlayers.get()) return false;
            return p.getServer() != null && p.getServer().isPvpAllowed();
        }
        return true;
    }

    /** Cible vivante visée dans la portée donnée, sans obstacle de bloc entre les deux. */
    @Nullable
    private static LivingEntity findTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                e -> validTarget(p, e), 0.35F);
        if (hit == null) return null;
        BlockHitResult block = p.level().clip(new ClipContext(eye, hit.getLocation(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) return null;
        return hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static List<LivingEntity> around(ServerPlayer p, double radius) {
        return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> validTarget(p, e));
    }

    // ------------------------------------------------------------------ capacités
    private static boolean absorb(ServerPlayer p, ParasiteData d) {
        LivingEntity target = findTarget(p, ParasiteConfig.COMMON.absorbRange.get());
        if (target == null || !BiomassTable.isAbsorbable(target)) {
            warn(p, "Aucune cible absorbable", "Visez une créature à portée");
            return false;
        }
        double threshold = ParasiteConfig.COMMON.absorbHealthThreshold.get();
        if (target.getHealth() > target.getMaxHealth() * threshold) {
            warn(p, "Cible trop résistante", "Affaiblissez-la sous " + Math.round(threshold * 100) + " % de sa santé");
            return false;
        }
        if (target.isRemoved() || !target.isAlive()) return false; // déjà absorbée : pas de double récompense

        boolean mutant = target.getPersistentData().getBoolean(ParasiteService.MUTANT_TAG);
        BiomassTable.Entry entry = BiomassTable.entryFor(target);
        double speciesBonus = 1.0 + Math.min(0.25, d.species.size() * 0.01);
        double raw = entry.biomass() * (mutant ? 3.0 : 1.0) * ParasiteConfig.COMMON.biomassMultiplier.get()
                * Progression.absorbEfficiency(d.level) * speciesBonus;
        int gain = (int) Math.max(1, Math.round(raw));

        ServerLevel level = p.serverLevel();
        String speciesId = BiomassTable.idOf(target.getType());
        String name = target.getType().getDescription().getString();
        double tx = target.getX(), ty = target.getY() + target.getBbHeight() * 0.5, tz = target.getZ();

        // La cible disparaît sans butin ni expérience vanilla : la récompense est celle du parasite.
        target.discard();

        ParasiteService.burst(level, ParasiteService.dust(), tx, ty, tz, 30, 0.4, 0.1);
        ParasiteService.burst(level, ParticleTypes.SQUID_INK, tx, ty, tz, 12, 0.3, 0.05);
        ParasiteService.playAt(level, tx, ty, tz, SoundEvents.SLIME_SQUISH, 1.2f, 0.5f);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, 0.5f, 0.6f);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), ModSounds.ABSORB.get(), 1.0f, 1.0f);

        boolean newSpecies = d.species.add(speciesId);
        d.categoryMask |= entry.category().mask();
        d.absorptions++;
        if (mutant) d.mutantsDefeated++;
        d.biomass += gain;
        d.totalBiomass += gain;
        d.hunger = Math.min(100f, d.hunger + gain * 0.5f);
        d.energy = Math.min(d.maxEnergy(), d.energy + gain * 0.2f);
        d.dirty = true;

        ParasiteService.notify(p, NotifyPacket.GAIN, "+" + gain + " biomasse", name + (mutant ? " (mutant)" : "") + " absorbé");
        if (newSpecies) {
            ParasiteService.notify(p, NotifyPacket.INFO, "Nouvelle espèce", name + " ajouté à la collection");
        }
        ParasiteService.addExperience(p, d, gain);
        return true;
    }

    private static boolean claws(ServerPlayer p, ParasiteData d, ParasiteForm form) {
        LivingEntity target = findTarget(p, 3.8);
        if (target == null) return false;
        double power = ParasiteService.powerMultiplier(d);
        float damage = (float) ((5.0 + form.attackBonus * 0.5) * power);
        ParasiteService.dealAbilityDamage(p, target, damage);
        for (LivingEntity other : target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.6),
                e -> e != target && validTarget(p, e))) {
            ParasiteService.dealAbilityDamage(p, other, damage * 0.6f);
        }
        ServerLevel level = p.serverLevel();
        ParasiteService.burst(level, ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 14, 0.35, 0.3);
        ParasiteService.burst(level, ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.2, 0.05);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
        return true;
    }

    private static boolean whip(ServerPlayer p, ParasiteData d) {
        LivingEntity target = findTarget(p, 9.0);
        if (target == null) {
            warn(p, "Aucune cible", "Visez une créature à moins de 9 blocs");
            return false;
        }
        ParasiteService.dealAbilityDamage(p, target, (float) (6.0 * ParasiteService.powerMultiplier(d)));
        Vec3 pull = p.position().subtract(target.position());
        if (pull.lengthSqr() > 1.0e-4) {
            Vec3 n = pull.normalize().scale(0.7);
            target.setDeltaMovement(target.getDeltaMovement().add(n.x, 0.25, n.z));
            target.hurtMarked = true;
        }
        ServerLevel level = p.serverLevel();
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        int steps = Math.min(24, (int) (from.distanceTo(to) * 2.0) + 1);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.SQUID_INK, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.5f);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), ModSounds.TENTACLE.get(), 1.0f, 1.0f);
        return true;
    }

    private static boolean leap(ServerPlayer p, ParasiteData d) {
        if (!p.onGround()) {
            warn(p, "Impossible en l'air", "Le bond prédateur demande un appui au sol");
            return false;
        }
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0, look.z);
        if (dir.lengthSqr() < 1.0e-4) return false;
        dir = dir.normalize();
        // Vérifications de collision : place au-dessus de la tête et devant soi.
        if (!p.level().noCollision(p, p.getBoundingBox().move(0.0, 1.2, 0.0))) {
            warn(p, "Plafond trop bas", "Pas assez de place pour bondir");
            return false;
        }
        if (!p.level().noCollision(p, p.getBoundingBox().move(dir.x, 0.5, dir.z))) {
            warn(p, "Obstacle", "Un bloc vous bloque le passage");
            return false;
        }
        double horizontal = Math.min(1.7, 1.15 + d.level * 0.004);
        p.setDeltaMovement(dir.x * horizontal, 0.62, dir.z * horizontal);
        p.hurtMarked = true;
        p.fallDistance = 0.0f;
        d.leapGraceTicks = 60; // réduit les dégâts de chute (voir ServerEvents)
        ServerLevel level = p.serverLevel();
        ParasiteService.burst(level, ParasiteService.dust(), p.getX(), p.getY() + 0.2, p.getZ(), 16, 0.4, 0.05);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_FLAP, 1.2f, 0.5f);
        return true;
    }

    private static boolean roar(ServerPlayer p, ParasiteData d) {
        double radius = 8.0 * (1.0 + d.level * 0.003);
        for (LivingEntity e : around(p, radius)) {
            if (e.distanceToSqr(p) > radius * radius) continue;
            e.knockback(1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            if (e instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
        ServerLevel level = p.serverLevel();
        ring(level, p, radius * 0.6, 24);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.6f);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), ModSounds.ROAR.get(), 1.6f, 1.0f);
        return true;
    }

    private static boolean carapace(ServerPlayer p, ParasiteData d) {
        d.carapaceTicks = (int) (160 * Progression.levelDuration(d.level));
        ServerLevel level = p.serverLevel();
        ParasiteService.burst(level, ParasiteService.dust(), p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.6, 0.02);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.5f);
        ParasiteService.syncVisual(p, true);
        return true;
    }

    private static boolean vision(ServerPlayer p, ParasiteData d) {
        int duration = (int) (300 * Progression.levelDuration(d.level));
        d.visionTicks = duration;
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration + 40, 0, false, false, false));
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0),
                e -> e != p && e instanceof Mob)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, false));
        }
        ParasiteService.playAt(p.serverLevel(), p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_STARE, 0.4f, 0.6f);
        return true;
    }

    private static boolean regen(ServerPlayer p, ParasiteData d) {
        if (d.biomass < 2) {
            warn(p, "Biomasse insuffisante", "La régénération consomme 2 biomasse par seconde");
            return false;
        }
        d.regenTicks = Math.max(1, (int) (10 * Progression.levelDuration(d.level))) * 20;
        ParasiteService.playAt(p.serverLevel(), p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_SQUISH, 0.8f, 0.4f);
        return true;
    }

    private static boolean camouflage(ServerPlayer p, ParasiteData d) {
        int duration = (int) (200 * Progression.levelDuration(d.level));
        d.camoTicks = duration;
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false));
        ServerLevel level = p.serverLevel();
        ParasiteService.burst(level, ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.5, 0.02);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, 1.0f, 0.5f);
        ParasiteService.syncVisual(p, true);
        return true;
    }

    private static boolean burst(ServerPlayer p, ParasiteData d) {
        double radius = 6.5;
        float damage = (float) (8.0 * ParasiteService.powerMultiplier(d));
        for (LivingEntity e : around(p, radius)) {
            if (e.distanceToSqr(p) > radius * radius) continue;
            ParasiteService.dealAbilityDamage(p, e, damage);
            e.knockback(1.5, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        ServerLevel level = p.serverLevel();
        ring(level, p, radius * 0.5, 20);
        ring(level, p, radius * 0.9, 28);
        ParasiteService.playAt(level, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, 1.0f, 0.5f);
        return true;
    }

    private static boolean ultimate(ServerPlayer p, ParasiteData d) {
        if (d.hasForm(ParasiteForm.ABOMINATION)) {
            warn(p, "Déjà débloquée", "Utilisez l'écran Évolutions pour changer de forme");
            return false;
        }
        if (d.biomass < 500) {
            warn(p, "Biomasse insuffisante", "500 biomasse requise (vous avez " + d.biomass + ")");
            return false;
        }
        d.biomass -= 500;
        ParasiteService.unlockForm(p, d, ParasiteForm.ABOMINATION);
        ParasiteService.applyForm(p, d, ParasiteForm.ABOMINATION);
        ring(p.serverLevel(), p, 3.0, 30);
        return true;
    }

    /** Anneau de particules sombres autour du joueur (plafonné par la configuration). */
    private static void ring(ServerLevel level, ServerPlayer p, double radius, int points) {
        int count = Math.min(points, ParasiteConfig.COMMON.maxParticlesPerEffect.get());
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0) * i / count;
            level.sendParticles(ParticleTypes.SQUID_INK, p.getX() + Math.cos(a) * radius, p.getY() + 0.2,
                    p.getZ() + Math.sin(a) * radius, 1, 0.05, 0.1, 0.05, 0.01);
        }
    }
}

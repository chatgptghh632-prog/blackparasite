package com.blackparasite.logic;

import com.blackparasite.config.ParasiteConfig;

/**
 * Formule de progression : l'expérience nécessaire pour passer du niveau n au niveau n+1 est
 * {@code 8 * n^1.5 * difficulté}. Les premiers niveaux sont donc rapides (≈ 8 xp, 22 xp, 41 xp…)
 * et les derniers demandent plusieurs centaines d'expérience chacun (niveau 99 → ≈ 7 800 xp).
 * Le cumul pour atteindre le niveau 100 est d'environ 32 000 xp avec la difficulté 1.0.
 */
public final class Progression {
    public static final int MAX_LEVEL = 100;

    private Progression() {}

    public record LevelInfo(int level, long into, long needed) {}

    public static long xpForNext(int level) {
        double difficulty = ParasiteConfig.COMMON.progressionDifficulty.get();
        return Math.max(5L, Math.round(8.0 * Math.pow(Math.max(1, level), 1.5) * difficulty));
    }

    public static LevelInfo info(long totalXp) {
        int level = 1;
        long remaining = Math.max(0L, totalXp);
        while (level < MAX_LEVEL) {
            long need = xpForNext(level);
            if (remaining < need) return new LevelInfo(level, remaining, need);
            remaining -= need;
            level++;
        }
        return new LevelInfo(MAX_LEVEL, 0L, 0L);
    }

    public static int levelFor(long totalXp) {
        return info(totalXp).level();
    }

    public static int maxEnergy(int level) {
        return 100 + level * 2;
    }

    /** Santé maximale supplémentaire apportée par le niveau (hors forme). */
    public static double levelHealthBonus(int level) {
        return level * 0.15;
    }

    public static double levelSpeedBonus(int level) {
        return level * 0.0004;
    }

    public static double levelResistance(int level) {
        return Math.min(0.2, level * 0.002);
    }

    /** Multiplicateur de puissance des attaques et capacités liées au niveau. */
    public static double levelPower(int level) {
        return 1.0 + level * 0.01;
    }

    /** Multiplicateur de durée des capacités temporaires. */
    public static double levelDuration(int level) {
        return 1.0 + level * 0.005;
    }

    /** Efficacité d'absorption : +0,5 % par niveau. */
    public static double absorbEfficiency(int level) {
        return 1.0 + level * 0.005;
    }
}

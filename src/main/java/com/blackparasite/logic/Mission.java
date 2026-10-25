package com.blackparasite.logic;

import java.util.function.ToIntFunction;

import com.blackparasite.data.ParasiteData;

/**
 * Missions facultatives. Chaque mission a un objectif mesurable, une récompense (biomasse + expérience)
 * et un suivi dans l'écran « Évolutions ».
 * <p>
 * La mission « Découvrir un lieu contaminé » du cahier des charges n'est pas incluse : elle nécessite une
 * génération de structures qui n'est pas encore développée (voir README).
 */
public enum Mission {
    FIRST_INFECTION("Première infection", "Se laisser infecter par le parasite.", 1, 25,
            d -> d.infected ? 1 : 0),
    FIRST_ABSORPTION("Première absorption", "Absorber une créature.", 1, 40,
            d -> d.absorptions),
    PREDATOR_STAGE("Stade de prédateur", "Atteindre le niveau 15.", 15, 150,
            d -> d.level),
    VARIED_DIET("Régime varié", "Absorber des créatures de 3 catégories différentes.", 3, 200,
            d -> Integer.bitCount(d.categoryMask)),
    ADVANCED_ABILITY("Capacité avancée", "Atteindre le niveau 55 et débloquer l'Explosion de tentacules.", 55, 600,
            d -> d.level),
    MUTANT("Créature mutante", "Absorber ou tuer une créature mutante.", 1, 500,
            d -> d.mutantsDefeated),
    ULTIMATE_FORM("Forme ultime", "Débloquer l'Abomination.", 1, 2000,
            d -> d.hasForm(ParasiteForm.ABOMINATION) ? 1 : 0);

    public final String title;
    public final String description;
    public final int target;
    public final int reward;
    private final ToIntFunction<ParasiteData> progress;

    Mission(String title, String description, int target, int reward, ToIntFunction<ParasiteData> progress) {
        this.title = title;
        this.description = description;
        this.target = target;
        this.reward = reward;
        this.progress = progress;
    }

    public int progress(ParasiteData data) {
        return Math.min(target, progress.applyAsInt(data));
    }

    public boolean isDone(ParasiteData data) {
        return progress.applyAsInt(data) >= target;
    }

    public int mask() {
        return 1 << ordinal();
    }
}

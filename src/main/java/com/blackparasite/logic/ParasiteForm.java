package com.blackparasite.logic;

import java.util.Locale;

/**
 * Les cinq formes du parasite. Chaque forme modifie réellement le gameplay (statistiques ci-dessous)
 * et possède son propre modèle 3D côté client (voir client.model.ParasiteModel).
 */
public enum ParasiteForm {
    //            id          nom                 niv  pv   vitesse atq   dégâts rés.  régén  saut  kbRes échelle tentacules  oeil RGB
    PORTEUR     ("porteur",     "Le Porteur",       1,  2.0, 0.00,  0.5,  1.00,  0.05, 1.5,  1.00, 0.0,  1.00, 2,  0xFF5A1E,
            "Le parasite recouvre partiellement votre corps. Forme de base de la progression."),
    PREDATEUR   ("predateur",   "Le Prédateur",    15,  6.0, 0.15,  2.0,  1.25,  0.10, 2.0,  1.35, 0.0,  1.05, 4,  0xFF2A14,
            "Bras allongés, griffes développées, bonds puissants et rugissement."),
    FANTOME     ("fantome",     "Le Fantôme Noir", 30,  4.0, 0.30,  1.5,  1.15,  0.05, 3.0,  1.20, 0.0,  1.00, 6,  0xC81E3C,
            "Silhouette dissoute dans la fumée : rapide, discrète, tentacules à distance."),
    COLOSSE     ("colosse",     "Le Colosse",      50, 20.0, -0.12, 5.0,  1.60,  0.30, 1.5,  0.90, 0.8,  1.30, 4,  0xFF0A0A,
            "Masse monstrueuse : attaques lourdes, résistance accrue, vitesse réduite."),
    ABOMINATION ("abomination", "L'Abomination",   80, 30.0, 0.10,  8.0,  2.00,  0.35, 3.5,  1.50, 0.5,  1.40, 8,  0xFF1450,
            "Forme ultime. Plusieurs yeux, tentacules nombreux, silhouette asymétrique.");

    public final String id;
    public final String displayName;
    /** Niveau minimal pour débloquer la forme (l'Abomination demande en plus la capacité ultime). */
    public final int unlockLevel;
    public final double healthBonus;
    /** Bonus de vitesse en proportion (ajouté via un modificateur MULTIPLY_BASE). */
    public final double speedBonus;
    public final double attackBonus;
    /** Multiplicateur des dégâts infligés. */
    public final double damageMultiplier;
    /** Réduction des dégâts subis (0..1). */
    public final double resistance;
    /** Énergie régénérée par seconde. */
    public final double energyRegen;
    public final double jumpMultiplier;
    public final double knockbackResistance;
    public final float visualScale;
    /** Nombre de chaînes de tentacules du modèle. */
    public final int tentacles;
    public final int eyeColor;
    public final String description;

    ParasiteForm(String id, String displayName, int unlockLevel, double healthBonus, double speedBonus,
                 double attackBonus, double damageMultiplier, double resistance, double energyRegen,
                 double jumpMultiplier, double knockbackResistance, double visualScale, int tentacles,
                 int eyeColor, String description) {
        this.id = id;
        this.displayName = displayName;
        this.unlockLevel = unlockLevel;
        this.healthBonus = healthBonus;
        this.speedBonus = speedBonus;
        this.attackBonus = attackBonus;
        this.damageMultiplier = damageMultiplier;
        this.resistance = resistance;
        this.energyRegen = energyRegen;
        this.jumpMultiplier = jumpMultiplier;
        this.knockbackResistance = knockbackResistance;
        this.visualScale = (float) visualScale;
        this.tentacles = tentacles;
        this.eyeColor = eyeColor;
        this.description = description;
    }

    public int mask() {
        return 1 << ordinal();
    }

    public static ParasiteForm byOrdinal(int ordinal) {
        ParasiteForm[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public static ParasiteForm byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (ParasiteForm f : values()) {
            if (f.id.equals(s) || f.name().toLowerCase(Locale.ROOT).equals(s)) return f;
        }
        return null;
    }
}

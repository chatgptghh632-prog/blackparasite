package com.blackparasite.logic;

import java.util.Locale;

/**
 * Les capacités du parasite. Le coût, la recharge et la disponibilité par forme sont définis ici ;
 * le coût peut être multiplié par la configuration et chaque capacité peut être désactivée.
 */
public enum ParasiteAbility {
    //          id            nom                          niv  énergie recharge(ticks) formes autorisées
    ABSORB     ("absorb",     "Absorption",                  1,   5, 30,  all(),
            "Absorbe une créature affaiblie pour gagner biomasse, expérience et faim."),
    CLAWS      ("claws",      "Griffes parasitaires",        1,   8, 20,  all(),
            "Frappe rapprochée améliorée qui touche aussi les créatures voisines."),
    WHIP       ("whip",       "Fouet de tentacule",          8,  15, 60,  all(),
            "Un tentacule frappe et attire une cible jusqu'à 9 blocs."),
    LEAP       ("leap",       "Bond prédateur",             15,  20, 80,  forms(ParasiteForm.PREDATEUR, ParasiteForm.ABOMINATION),
            "Un bond puissant vers l'avant (refusé si la place manque)."),
    ROAR       ("roar",       "Rugissement terrifiant",     20,  25, 200, forms(ParasiteForm.PREDATEUR, ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION),
            "Repousse et ralentit les créatures proches et leur fait perdre leur cible."),
    CARAPACE   ("carapace",   "Carapace organique",         22,  30, 400, forms(ParasiteForm.PREDATEUR, ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION),
            "Réduit de 40 % les dégâts subis pendant quelques secondes."),
    VISION     ("vision",     "Vision du prédateur",        25,  15, 400, forms(ParasiteForm.PREDATEUR, ParasiteForm.FANTOME, ParasiteForm.ABOMINATION),
            "Vision nocturne et contour lumineux des créatures proches."),
    REGEN      ("regen",      "Régénération parasitaire",   28,  20, 600, forms(ParasiteForm.PREDATEUR, ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION),
            "Soigne en consommant de la biomasse (2 par seconde) pendant 10 s."),
    CAMOUFLAGE ("camouflage", "Camouflage sombre",          30,  30, 300, forms(ParasiteForm.FANTOME, ParasiteForm.ABOMINATION),
            "Invisibilité de 10 s. Brisée par une attaque ; les créatures à moins de 4 blocs vous voient."),
    BURST      ("burst",      "Explosion de tentacules",    55,  60, 500, forms(ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION),
            "Attaque de zone dévastatrice autour de vous."),
    ULTIMATE   ("ultimate",   "Transformation ultime",      80, 100, 1200, forms(ParasiteForm.COLOSSE),
            "Débloque et adopte l'Abomination (coûte 500 biomasse et 100 énergie).");

    public final String id;
    public final String displayName;
    public final int minLevel;
    public final int energyCost;
    public final int cooldownTicks;
    public final int formMask;
    public final String description;

    ParasiteAbility(String id, String displayName, int minLevel, int energyCost, int cooldownTicks,
                    int formMask, String description) {
        this.id = id;
        this.displayName = displayName;
        this.minLevel = minLevel;
        this.energyCost = energyCost;
        this.cooldownTicks = cooldownTicks;
        this.formMask = formMask;
        this.description = description;
    }

    private static int all() {
        int m = 0;
        for (ParasiteForm f : ParasiteForm.values()) m |= f.mask();
        return m;
    }

    private static int forms(ParasiteForm... forms) {
        int m = 0;
        for (ParasiteForm f : forms) m |= f.mask();
        return m;
    }

    public boolean allowedIn(ParasiteForm form) {
        return form != null && (formMask & form.mask()) != 0;
    }

    public static ParasiteAbility byOrdinal(int ordinal) {
        ParasiteAbility[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }

    public static ParasiteAbility byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (ParasiteAbility a : values()) if (a.id.equals(s)) return a;
        return null;
    }

    /** Chemin de l'icône : assets/blackparasite/textures/gui/icon_&lt;id&gt;.png */
    public String iconPath() {
        return "textures/gui/icon_" + id + ".png";
    }
}

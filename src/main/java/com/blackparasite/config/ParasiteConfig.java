package com.blackparasite.config;

import java.util.EnumMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.blackparasite.logic.ParasiteAbility;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Deux fichiers de configuration :
 * <ul>
 *   <li>COMMON  → config/blackparasite-common.toml : règles de jeu, lues par le serveur (ou le jeu solo).</li>
 *   <li>CLIENT  → config/blackparasite-client.toml  : HUD et options visuelles, propres à chaque joueur.</li>
 * </ul>
 */
public final class ParasiteConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private ParasiteConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.DoubleValue progressionDifficulty;
        public final ForgeConfigSpec.DoubleValue biomassMultiplier;
        public final ForgeConfigSpec.DoubleValue killXpRatio;
        public final ForgeConfigSpec.DoubleValue hungerDrainPerSecond;
        public final ForgeConfigSpec.DoubleValue energyCostMultiplier;
        public final ForgeConfigSpec.DoubleValue absorbHealthThreshold;
        public final ForgeConfigSpec.DoubleValue absorbRange;
        public final ForgeConfigSpec.BooleanValue allowAbsorbVillagers;
        public final ForgeConfigSpec.BooleanValue abilitiesAffectPlayers;
        public final ForgeConfigSpec.BooleanValue multiplayerVisualSync;
        public final ForgeConfigSpec.BooleanValue keepFormOnDeath;
        public final ForgeConfigSpec.DoubleValue eventFrequency;
        public final ForgeConfigSpec.DoubleValue mutantChance;
        public final ForgeConfigSpec.IntValue maxParticlesPerEffect;
        public final Map<ParasiteAbility, ForgeConfigSpec.BooleanValue> abilityEnabled = new EnumMap<>(ParasiteAbility.class);

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Progression").push("progression");
            progressionDifficulty = b.comment("Multiplie l'expérience nécessaire à chaque niveau (1.0 = normal, 2.0 = deux fois plus long).")
                    .defineInRange("difficulty", 1.0, 0.1, 10.0);
            biomassMultiplier = b.comment("Multiplie la biomasse obtenue par absorption.")
                    .defineInRange("biomassMultiplier", 1.0, 0.1, 20.0);
            killXpRatio = b.comment("Fraction de la valeur en biomasse accordée en expérience quand une créature est tuée sans être absorbée.")
                    .defineInRange("killXpRatio", 0.25, 0.0, 1.0);
            b.pop();

            b.comment("Besoins du parasite").push("needs");
            hungerDrainPerSecond = b.comment("Faim perdue par seconde (0 = désactivé, la jauge va de 0 à 100).")
                    .defineInRange("hungerDrainPerSecond", 0.05, 0.0, 5.0);
            energyCostMultiplier = b.comment("Multiplie le coût en énergie de toutes les capacités.")
                    .defineInRange("energyCostMultiplier", 1.0, 0.0, 10.0);
            b.pop();

            b.comment("Absorption").push("absorption");
            absorbHealthThreshold = b.comment("Une créature ne peut être absorbée que si sa santé est inférieure à cette fraction de sa santé maximale.")
                    .defineInRange("healthThreshold", 0.35, 0.05, 1.0);
            absorbRange = b.comment("Portée de l'absorption en blocs.")
                    .defineInRange("range", 4.5, 2.0, 10.0);
            allowAbsorbVillagers = b.comment("Autorise l'absorption des villageois et marchands.")
                    .define("allowVillagers", false);
            b.pop();

            b.comment("Multijoueur").push("multiplayer");
            abilitiesAffectPlayers = b.comment("Si faux, aucune capacité du parasite ne peut blesser, repousser ni absorber un autre joueur.")
                    .define("abilitiesAffectPlayers", false);
            multiplayerVisualSync = b.comment("Si vrai, les autres joueurs voient votre forme de parasite. Sinon seul vous la voyez.")
                    .define("visualSync", true);
            b.pop();

            b.comment("Mort et réapparition").push("death");
            keepFormOnDeath = b.comment("Garde la forme actuelle après la mort (la progression est toujours conservée).")
                    .define("keepFormOnDeath", true);
            b.pop();

            b.comment("Événements du monde").push("events");
            eventFrequency = b.comment("Fréquence des manifestations ambiantes (particules, sons) autour des porteurs. 0 = désactivé.")
                    .defineInRange("frequency", 1.0, 0.0, 5.0);
            mutantChance = b.comment("Probabilité qu'un monstre apparaissant près d'un porteur (64 blocs) devienne une créature mutante. 0 = désactivé.")
                    .defineInRange("mutantChance", 0.03, 0.0, 1.0);
            maxParticlesPerEffect = b.comment("Plafond de particules envoyées par effet serveur (performances).")
                    .defineInRange("maxParticlesPerEffect", 40, 5, 200);
            b.pop();

            b.comment("Activer ou désactiver chaque capacité").push("abilities");
            for (ParasiteAbility a : ParasiteAbility.values()) {
                abilityEnabled.put(a, b.define(a.id, true));
            }
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue hudEnabled;
        public final ForgeConfigSpec.DoubleValue hudScale;
        public final ForgeConfigSpec.DoubleValue hudOpacity;
        public final ForgeConfigSpec.IntValue hudOffsetX;
        public final ForgeConfigSpec.IntValue hudOffsetY;
        public final ForgeConfigSpec.BooleanValue showHealth;
        public final ForgeConfigSpec.BooleanValue showEvolution;
        public final ForgeConfigSpec.BooleanValue showHunger;
        public final ForgeConfigSpec.BooleanValue showEnergy;
        public final ForgeConfigSpec.BooleanValue showBiomass;
        public final ForgeConfigSpec.BooleanValue showAbility;
        public final ForgeConfigSpec.BooleanValue showAlerts;
        public final ForgeConfigSpec.BooleanValue advancedAnimations;
        public final ForgeConfigSpec.BooleanValue uiSounds;
        public final ForgeConfigSpec.BooleanValue menuMusic;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Interface en jeu (HUD)").push("hud");
            hudEnabled = b.define("enabled", true);
            hudScale = b.defineInRange("scale", 1.0, 0.5, 2.5);
            hudOpacity = b.defineInRange("opacity", 0.9, 0.1, 1.0);
            hudOffsetX = b.comment("Distance au bord gauche de l'écran, en pixels.").defineInRange("offsetX", 6, 0, 2000);
            hudOffsetY = b.comment("Distance au bord bas de l'écran, en pixels.").defineInRange("offsetY", 6, 0, 2000);
            showHealth = b.define("showHealth", true);
            showEvolution = b.define("showEvolution", true);
            showHunger = b.define("showHunger", true);
            showEnergy = b.define("showEnergy", true);
            showBiomass = b.define("showBiomass", true);
            showAbility = b.define("showAbility", true);
            showAlerts = b.define("showAlerts", true);
            b.pop();

            b.comment("Effets visuels").push("visuals");
            advancedAnimations = b.comment("Animations avancées (pulsation des yeux, particules de menu, tentacules plus amples).")
                    .define("advancedAnimations", true);
            uiSounds = b.define("uiSounds", true);
            menuMusic = b.comment("Musique d'ambiance facultative dans le menu principal du parasite.").define("menuMusic", true);
            b.pop();
        }
    }
}

package com.blackparasite.logic;

import java.util.function.Predicate;

import com.blackparasite.data.ParasiteData;

/**
 * Entrées du journal du parasite, débloquées par la progression. Elles racontent l'origine de l'entité.
 */
public enum Lore {
    SUBJECT_ZERO("Sujet Zéro",
            "Une expérience abandonnée a laissé une chose vivante dans le noir. Elle ne parle pas : elle observe, "
                    + "et elle a choisi son premier hôte.",
            d -> d.infected),
    HUNGER("La faim",
            "Le parasite ne se nourrit pas de chair mais de ce qui anime la chair. Chaque créature absorbée "
                    + "lui laisse un souvenir de l'espèce.",
            d -> d.absorptions >= 1),
    TASTE("Le goût du changement",
            "Après quelques proies, le corps de l'hôte commence à s'étirer. Les notes du laboratoire parlent "
                    + "de « sélection forcée » : le parasite garde ce qui tue, jette le reste.",
            d -> d.level >= 10),
    PREDATOR("Le Prédateur",
            "Les bras s'allongent, les yeux grandissent. Le parasite ne cache plus ce qu'il est ; "
                    + "l'hôte, lui, ne remarque presque plus la différence.",
            d -> d.hasForm(ParasiteForm.PREDATEUR)),
    GHOST("Le Fantôme Noir",
            "Dans l'obscurité, la peau devient fumée. Les rapports mentionnent des témoins qui jurent avoir "
                    + "été regardés pendant des nuits entières avant l'attaque.",
            d -> d.hasForm(ParasiteForm.FANTOME)),
    COLOSSUS("Le Colosse",
            "Un corps conçu pour encaisser. L'expérience n'était pas un remède : c'était une arme qui "
                    + "attendait d'être portée.",
            d -> d.hasForm(ParasiteForm.COLOSSE)),
    MUTANT("Les contaminés",
            "Le parasite ne reste pas dans un seul hôte. Certaines créatures proches de vous muent, "
                    + "gonflées d'une faim qu'elles ne comprennent pas.",
            d -> d.mutantsDefeated >= 1),
    ORIGIN("L'origine",
            "Vous n'avez pas été choisi par hasard : le parasite cherchait un hôte capable de supporter "
                    + "toutes ses formes. L'Abomination est ce qu'il était censé devenir dès le début.",
            d -> d.hasForm(ParasiteForm.ABOMINATION));

    public final String title;
    public final String text;
    private final Predicate<ParasiteData> condition;

    Lore(String title, String text, Predicate<ParasiteData> condition) {
        this.title = title;
        this.text = text;
        this.condition = condition;
    }

    public boolean isUnlockedBy(ParasiteData data) {
        return condition.test(data);
    }

    public int mask() {
        return 1 << ordinal();
    }
}

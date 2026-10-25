package com.blackparasite.command;

import java.util.Arrays;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.Mission;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.logic.ParasiteService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /blackparasite — commandes de test réservées aux opérateurs (niveau de permission 2).
 * <pre>
 *   /blackparasite stats [joueur]
 *   /blackparasite infect &lt;joueur&gt;
 *   /blackparasite unlock &lt;joueur&gt; &lt;forme&gt;
 *   /blackparasite biomass add &lt;joueur&gt; &lt;quantité&gt;
 *   /blackparasite reset &lt;joueur&gt;
 *   /blackparasite checkconfig
 * </pre>
 */
@Mod.EventBusSubscriber(modid = BlackParasiteMod.MODID)
public final class ParasiteCommand {

    private static final SuggestionProvider<CommandSourceStack> FORM_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(ParasiteForm.values()).map(f -> f.id), builder);

    private ParasiteCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blackparasite")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stats")
                        .executes(c -> stats(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> stats(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("infect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> infect(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("form", StringArgumentType.word())
                                        .suggests(FORM_SUGGESTIONS)
                                        .executes(c -> unlock(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                StringArgumentType.getString(c, "form"))))))
                .then(Commands.literal("biomass")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                                .executes(c -> addBiomass(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        IntegerArgumentType.getInteger(c, "amount")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reset(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("checkconfig")
                        .executes(c -> checkConfig(c.getSource()))));
    }

    private static void say(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int stats(CommandSourceStack src, ServerPlayer target) {
        ParasiteData d = ParasiteService.get(target);
        if (d == null || !d.infected) {
            say(src, target.getGameProfile().getName() + " n'est pas infecté.");
            return 0;
        }
        ParasiteForm form = d.currentForm();
        say(src, "=== Parasite de " + target.getGameProfile().getName() + " ===");
        say(src, "Forme : " + (form == null ? "?" : form.displayName) + " | Niveau " + d.level + " | XP " + d.experience);
        say(src, "Biomasse : " + d.biomass + " (totale " + d.totalBiomass + ") | Absorptions : " + d.absorptions
                + " | Espèces : " + d.species.size());
        say(src, String.format("Faim : %.1f/100 | Énergie : %.1f/%.0f", d.hunger, d.energy, d.maxEnergy()));
        StringBuilder forms = new StringBuilder();
        for (ParasiteForm f : ParasiteForm.values()) if (d.hasForm(f)) forms.append(f.id).append(' ');
        say(src, "Formes débloquées : " + forms.toString().trim());
        say(src, "Missions : " + Integer.bitCount(d.missionMask) + "/" + Mission.values().length
                + " | Capacités utilisées : " + d.abilityUses + " | Mutants vaincus : " + d.mutantsDefeated);
        return 1;
    }

    private static int infect(CommandSourceStack src, ServerPlayer target) {
        if (ParasiteService.infect(target)) {
            say(src, target.getGameProfile().getName() + " est maintenant infecté.");
            return 1;
        }
        src.sendFailure(Component.literal("Impossible : déjà infecté."));
        return 0;
    }

    private static int unlock(CommandSourceStack src, ServerPlayer target, String formId) {
        ParasiteForm form = ParasiteForm.byId(formId);
        if (form == null) {
            src.sendFailure(Component.literal("Forme inconnue. Valeurs : "
                    + String.join(", ", Arrays.stream(ParasiteForm.values()).map(f -> f.id).toList())));
            return 0;
        }
        ParasiteData d = ParasiteService.get(target);
        if (d == null || !d.infected) {
            src.sendFailure(Component.literal("Le joueur n'est pas infecté (utilisez /blackparasite infect)."));
            return 0;
        }
        ParasiteService.unlockForm(target, d, form);
        ParasiteService.syncData(target);
        say(src, "Forme « " + form.displayName + " » débloquée pour " + target.getGameProfile().getName() + " (test).");
        return 1;
    }

    private static int addBiomass(CommandSourceStack src, ServerPlayer target, int amount) {
        ParasiteData d = ParasiteService.get(target);
        if (d == null || !d.infected) {
            src.sendFailure(Component.literal("Le joueur n'est pas infecté."));
            return 0;
        }
        d.biomass += amount;
        d.totalBiomass += amount;
        d.dirty = true;
        ParasiteService.addExperience(target, d, amount);
        ParasiteService.syncData(target);
        say(src, "+" + amount + " biomasse et expérience pour " + target.getGameProfile().getName() + ".");
        return 1;
    }

    private static int reset(CommandSourceStack src, ServerPlayer target) {
        ParasiteService.resetPlayer(target);
        say(src, "Données du parasite réinitialisées pour " + target.getGameProfile().getName() + ".");
        return 1;
    }

    private static int checkConfig(CommandSourceStack src) {
        ParasiteConfig.Common c = ParasiteConfig.COMMON;
        say(src, "=== Configuration (blackparasite-common.toml) ===");
        say(src, "difficulté = " + c.progressionDifficulty.get() + " | biomasse x" + c.biomassMultiplier.get()
                + " | xp de kill = " + c.killXpRatio.get());
        say(src, "faim/s = " + c.hungerDrainPerSecond.get() + " | coût énergie x" + c.energyCostMultiplier.get());
        say(src, "absorption : seuil " + c.absorbHealthThreshold.get() + ", portée " + c.absorbRange.get()
                + ", villageois = " + c.allowAbsorbVillagers.get());
        say(src, "événements = " + c.eventFrequency.get() + " | mutants = " + c.mutantChance.get()
                + " | capacités vs joueurs = " + c.abilitiesAffectPlayers.get());
        StringBuilder off = new StringBuilder();
        for (ParasiteAbility a : ParasiteAbility.values()) if (!c.abilityEnabled.get(a).get()) off.append(a.id).append(' ');
        say(src, "Capacités désactivées : " + (off.length() == 0 ? "aucune" : off.toString().trim()));
        return 1;
    }
}

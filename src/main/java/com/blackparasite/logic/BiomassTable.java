package com.blackparasite.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.blackparasite.config.ParasiteConfig;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Valeur en biomasse de chaque créature. Les créatures non listées (y compris celles d'autres mods)
 * reçoivent une valeur calculée à partir de leur santé maximale.
 */
public final class BiomassTable {

    public enum Category {
        SMALL("Petit animal"), ANIMAL("Animal"), HOSTILE("Hostile"), RARE("Rare");

        public final String label;

        Category(String label) {
            this.label = label;
        }

        public int mask() {
            return 1 << ordinal();
        }
    }

    public record Entry(int biomass, Category category) {}

    private static final Map<EntityType<?>, Entry> TABLE = new LinkedHashMap<>();

    private static void put(EntityType<?> type, int biomass, Category category) {
        TABLE.put(type, new Entry(biomass, category));
    }

    static {
        // Petits animaux
        put(EntityType.CHICKEN, 3, Category.SMALL);
        put(EntityType.RABBIT, 3, Category.SMALL);
        put(EntityType.BAT, 2, Category.SMALL);
        put(EntityType.COD, 2, Category.SMALL);
        put(EntityType.SALMON, 2, Category.SMALL);
        put(EntityType.SILVERFISH, 3, Category.SMALL);
        put(EntityType.ENDERMITE, 3, Category.SMALL);
        put(EntityType.PARROT, 4, Category.SMALL);
        put(EntityType.CAT, 4, Category.SMALL);
        put(EntityType.OCELOT, 5, Category.SMALL);
        put(EntityType.FROG, 3, Category.SMALL);
        put(EntityType.TURTLE, 5, Category.SMALL);
        // Animaux plus résistants
        put(EntityType.PIG, 6, Category.ANIMAL);
        put(EntityType.SHEEP, 6, Category.ANIMAL);
        put(EntityType.COW, 8, Category.ANIMAL);
        put(EntityType.MOOSHROOM, 9, Category.ANIMAL);
        put(EntityType.GOAT, 8, Category.ANIMAL);
        put(EntityType.FOX, 7, Category.ANIMAL);
        put(EntityType.WOLF, 9, Category.ANIMAL);
        put(EntityType.HORSE, 12, Category.ANIMAL);
        put(EntityType.DONKEY, 11, Category.ANIMAL);
        put(EntityType.MULE, 11, Category.ANIMAL);
        put(EntityType.LLAMA, 11, Category.ANIMAL);
        put(EntityType.POLAR_BEAR, 16, Category.ANIMAL);
        put(EntityType.PANDA, 14, Category.ANIMAL);
        put(EntityType.DOLPHIN, 10, Category.ANIMAL);
        // Créatures hostiles
        put(EntityType.ZOMBIE, 12, Category.HOSTILE);
        put(EntityType.HUSK, 14, Category.HOSTILE);
        put(EntityType.DROWNED, 14, Category.HOSTILE);
        put(EntityType.SKELETON, 12, Category.HOSTILE);
        put(EntityType.STRAY, 14, Category.HOSTILE);
        put(EntityType.SPIDER, 10, Category.HOSTILE);
        put(EntityType.CAVE_SPIDER, 8, Category.HOSTILE);
        put(EntityType.CREEPER, 14, Category.HOSTILE);
        put(EntityType.SLIME, 8, Category.HOSTILE);
        put(EntityType.PHANTOM, 12, Category.HOSTILE);
        put(EntityType.PILLAGER, 16, Category.HOSTILE);
        put(EntityType.VINDICATOR, 20, Category.HOSTILE);
        put(EntityType.BLAZE, 22, Category.HOSTILE);
        put(EntityType.GHAST, 30, Category.HOSTILE);
        put(EntityType.MAGMA_CUBE, 14, Category.HOSTILE);
        put(EntityType.ZOMBIFIED_PIGLIN, 16, Category.HOSTILE);
        put(EntityType.PIGLIN, 16, Category.HOSTILE);
        put(EntityType.HOGLIN, 20, Category.HOSTILE);
        put(EntityType.WITHER_SKELETON, 28, Category.HOSTILE);
        put(EntityType.GUARDIAN, 20, Category.HOSTILE);
        // Créatures rares
        put(EntityType.ENDERMAN, 40, Category.RARE);
        put(EntityType.WITCH, 45, Category.RARE);
        put(EntityType.EVOKER, 60, Category.RARE);
        put(EntityType.RAVAGER, 75, Category.RARE);
        put(EntityType.PIGLIN_BRUTE, 60, Category.RARE);
        put(EntityType.SHULKER, 50, Category.RARE);
        put(EntityType.ELDER_GUARDIAN, 150, Category.RARE);
        put(EntityType.IRON_GOLEM, 80, Category.RARE);
        put(EntityType.WARDEN, 600, Category.RARE);
    }

    private BiomassTable() {}

    /** Liste ordonnée des espèces connues, pour le menu de collection. */
    public static List<EntityType<?>> knownTypes() {
        return Collections.unmodifiableList(new ArrayList<>(TABLE.keySet()));
    }

    public static String idOf(EntityType<?> type) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key == null ? "unknown" : key.toString();
    }

    /** Une créature peut-elle être absorbée en principe ? (les boss, joueurs et villageois sont exclus). */
    public static boolean isAbsorbable(LivingEntity entity) {
        if (entity instanceof Player) return false;
        if (!(entity instanceof Mob)) return false;
        if (entity.getType().is(Tags.EntityTypes.BOSSES)) return false;
        if (entity.getType() == EntityType.VILLAGER || entity.getType() == EntityType.WANDERING_TRADER) {
            return ParasiteConfig.COMMON.allowAbsorbVillagers.get();
        }
        return true;
    }

    public static Entry entryFor(LivingEntity entity) {
        Entry e = TABLE.get(entity.getType());
        if (e != null) return e;
        int value = (int) Math.max(2, Math.min(60, Math.round(entity.getMaxHealth() * 0.8)));
        return new Entry(value, entity instanceof Enemy ? Category.HOSTILE : Category.ANIMAL);
    }
}

package com.metaphysicsnecrosis.metaphysicsspoilage;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration class for MetaphysicsSpoilage mod
 * Contains all configurable settings for the spoilage system
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Enums for configuration options
    public enum SpoilageMode {
        INSTANT_DISAPPEAR("instant_disappear"),
        TRANSFORM_TO_SPOILED("transform_to_spoiled");

        private final String name;

        SpoilageMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static SpoilageMode fromString(String name) {
            for (SpoilageMode mode : values()) {
                if (mode.name.equals(name)) {
                    return mode;
                }
            }
            return INSTANT_DISAPPEAR; // default
        }
    }

    public enum FoodBlockingMode {
        FULL_BLOCK("full_block"),
        ZERO_NUTRITION("zero_nutrition");

        private final String name;

        FoodBlockingMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static FoodBlockingMode fromString(String name) {
            for (FoodBlockingMode mode : values()) {
                if (mode.name.equals(name)) {
                    return mode;
                }
            }
            return FULL_BLOCK; // default
        }
    }

    // === GENERAL SETTINGS ===
    public static final ModConfigSpec.BooleanValue ENABLE_SPOILAGE_SYSTEM;
    public static final ModConfigSpec.DoubleValue SPOILAGE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.EnumValue<SpoilageMode> SPOILAGE_MODE;

    // === UNIVERSAL FOOD SETTINGS ===
    public static final ModConfigSpec.IntValue DEFAULT_FOOD_STORAGE_DAYS;
    public static final ModConfigSpec.BooleanValue ENABLE_UNIVERSAL_FOOD_SPOILAGE;

    // === EXCLUSION SETTINGS ===
    public static final ModConfigSpec.BooleanValue EXCLUDE_MAGICAL_FOOD;
    public static final ModConfigSpec.BooleanValue EXCLUDE_POTIONS_FROM_SPOILAGE;
    public static final ModConfigSpec.BooleanValue EXCLUDE_ALREADY_SPOILED_ITEMS;

    // === LEGACY EXAMPLE SETTINGS (for compatibility) ===
    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK;
    public static final ModConfigSpec.IntValue MAGIC_NUMBER;
    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS;

    // === FOOD EXCLUSION SETTINGS ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_EDIBLE_ITEMS;

    // === FOOD BLOCKING SETTINGS ===
    public static final ModConfigSpec.EnumValue<FoodBlockingMode> FOOD_BLOCKING_MODE;

    // === TOOLTIP SETTINGS ===
    public static final ModConfigSpec.BooleanValue ENABLE_TOOLTIP_SYSTEM;
    public static final ModConfigSpec.BooleanValue SHOW_DETAILED_TOOLTIPS;

    // === НАСТРОЙКИ ПРОИЗВОДИТЕЛЬНОСТИ ===
    public static final ModConfigSpec.BooleanValue ENABLE_PERFORMANCE_PROFILING;
    public static final ModConfigSpec.BooleanValue ENABLE_CACHE_OPTIMIZATION;
    public static final ModConfigSpec.IntValue CACHE_SIZE_LIMIT;
    public static final ModConfigSpec.IntValue GUI_REFRESH_INTERVAL_MS;
    public static final ModConfigSpec.BooleanValue ENABLE_BATCH_PROCESSING;
    public static final ModConfigSpec.BooleanValue SHOW_CONTAINER_TOOLTIPS;

    // === НАСТРОЙКИ ОБРАБОТКИ КОНТЕЙНЕРОВ ===
    public static final ModConfigSpec.BooleanValue ENABLE_GLOBAL_CONTAINER_PROCESSING;
    public static final ModConfigSpec.IntValue GLOBAL_CONTAINER_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue PLAYER_CONTAINER_CHECK_INTERVAL;

    static {
        // General settings
        BUILDER.comment("General spoilage system settings").push("general");

        ENABLE_SPOILAGE_SYSTEM = BUILDER
                .comment("Enable or disable the entire spoilage system")
                .define("enableSpoilageSystem", true);

        SPOILAGE_SPEED_MULTIPLIER = BUILDER
                .comment("Multiplier for spoilage speed (higher = faster spoilage)")
                .defineInRange("spoilageSpeedMultiplier", 1.0, 0.1, 10.0);

        SPOILAGE_MODE = BUILDER
                .comment("How spoiled food should behave: instant_disappear or transform_to_spoiled")
                .defineEnum("spoilageMode", SpoilageMode.TRANSFORM_TO_SPOILED);

        // Universal food settings
        BUILDER.comment("Universal food spoilage settings").push("universal_food");

        ENABLE_UNIVERSAL_FOOD_SPOILAGE = BUILDER
                .comment("Enable spoilage for ANY item with FOOD component from any mod")
                .define("enableUniversalFoodSpoilage", true);

        DEFAULT_FOOD_STORAGE_DAYS = BUILDER
                .comment("Default storage duration for unknown food items (days)")
                .defineInRange("defaultFoodStorageDays", 2, 1, 365);

        BUILDER.pop();

        // Exclusion settings
        BUILDER.comment("Automatic exclusion settings for certain food types").push("exclusions_auto");

        EXCLUDE_MAGICAL_FOOD = BUILDER
                .comment("Automatically exclude magical food items (golden apples, enchanted golden apples) from spoilage")
                .define("excludeMagicalFood", false);

        EXCLUDE_POTIONS_FROM_SPOILAGE = BUILDER
                .comment("Exclude all potions (normal, splash, lingering) from spoilage system")
                .define("excludePotionsFromSpoilage", false);

        EXCLUDE_ALREADY_SPOILED_ITEMS = BUILDER
                .comment("Exclude items that are already spoiled/corrupted (rotten flesh, spider eye) from spoilage")
                .define("excludeAlreadySpoiledItems", false);

        BUILDER.pop();

        // Legacy example settings (for compatibility)
        LOG_DIRT_BLOCK = BUILDER
                .comment("Whether to log the dirt block on common setup")
                .define("logDirtBlock", true);

        MAGIC_NUMBER = BUILDER
                .comment("A magic number")
                .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

        MAGIC_NUMBER_INTRODUCTION = BUILDER
                .comment("What you want the introduction message to be for the magic number")
                .define("magicNumberIntroduction", "The magic number is... ");

        ITEM_STRINGS = BUILDER
                .comment("A list of items to log on common setup.")
                .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

        BUILDER.pop();

        // Food exclusion settings
        BUILDER.comment("Food items that are excluded from the spoilage system").push("exclusions");

        EXCLUDED_ITEMS = BUILDER
                .comment("List of items that are completely excluded from the spoilage system")
                .defineListAllowEmpty("excludedItems", List.of(), () -> "", Config::validateItemName);

        ALWAYS_EDIBLE_ITEMS = BUILDER
                .comment("List of items that can always be eaten without timestamp (whitelist for non-timestamped food)")
                .defineListAllowEmpty("alwaysEdibleItems", List.of(), () -> "", Config::validateItemName);

        BUILDER.pop();

        // Food blocking settings
        BUILDER.comment("Settings for blocking food consumption without timestamps").push("food_blocking");

        FOOD_BLOCKING_MODE = BUILDER
                .comment("How to handle food without timestamps: full_block (cannot eat) or zero_nutrition (can eat but no hunger restored)")
                .defineEnum("foodBlockingMode", FoodBlockingMode.FULL_BLOCK);

        BUILDER.pop();

        // Tooltip settings
        BUILDER.comment("Settings for spoilage tooltips").push("tooltips");

        ENABLE_TOOLTIP_SYSTEM = BUILDER
                .comment("Enable or disable the tooltip system for spoiled food")
                .define("enableTooltipSystem", true);

        SHOW_DETAILED_TOOLTIPS = BUILDER
                .comment("Show detailed information in tooltips (creation day, spoilage day, etc.)")
                .define("showDetailedTooltips", true);

        SHOW_CONTAINER_TOOLTIPS = BUILDER
                .comment("Show tooltips for food containers with their contents")
                .define("showContainerTooltips", true);

        BUILDER.pop();

        // Performance settings
        BUILDER.comment("Performance optimization settings")
                .push("performance");
        ENABLE_PERFORMANCE_PROFILING = BUILDER
                .comment("Enable performance profiling and monitoring (may impact performance slightly)")
                .define("enablePerformanceProfiling", false);
        ENABLE_CACHE_OPTIMIZATION = BUILDER
                .comment("Enable cache optimization for better performance")
                .define("enableCacheOptimization", true);
        CACHE_SIZE_LIMIT = BUILDER
                .comment("Maximum number of entries in performance caches (higher = more memory, better performance)")
                .defineInRange("cacheSizeLimit", 1000, 100, 10000);
        GUI_REFRESH_INTERVAL_MS = BUILDER
                .comment("GUI refresh interval in milliseconds (lower = more responsive, higher = better performance)")
                .defineInRange("guiRefreshIntervalMs", 1000, 100, 5000);
        ENABLE_BATCH_PROCESSING = BUILDER
                .comment("Enable batch processing for better performance with large inventories")
                .define("enableBatchProcessing", true);
        BUILDER.pop();

        // Container processing settings
        BUILDER.comment("Container processing settings for real-time food timestamp application")
                .push("container_processing");
        ENABLE_GLOBAL_CONTAINER_PROCESSING = BUILDER
                .comment("Enable processing of ALL containers in loaded chunks (performance intensive)")
                .define("enableGlobalContainerProcessing", false);
        GLOBAL_CONTAINER_CHECK_INTERVAL = BUILDER
                .comment("Interval in ticks for checking all containers globally (20 ticks = 1 second)")
                .defineInRange("globalContainerCheckInterval", 60, 20, 1200);
        PLAYER_CONTAINER_CHECK_INTERVAL = BUILDER
                .comment("Interval in ticks for checking containers opened by players (faster)")
                .defineInRange("playerContainerCheckInterval", 20, 1, 60);
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    /**
     * Check if an item is excluded from the spoilage system
     *
     * @param itemId The item's resource location
     * @return true if the item should be excluded from spoilage
     */
    public static boolean isItemExcluded(String itemId) {
        return EXCLUDED_ITEMS.get().contains(itemId);
    }

    /**
     * Check if an item can always be eaten without timestamp
     *
     * @param itemId The item's resource location
     * @return true if the item can be eaten without timestamp
     */
    public static boolean isAlwaysEdible(String itemId) {
        return ALWAYS_EDIBLE_ITEMS.get().contains(itemId);
    }

    /**
     * УДАЛЕНО: getStorageDuration и getEffectiveSpoilageDuration
     * Все настройки времени порчи теперь управляются через JSON файлы:
     * - vanilla_minecraft.json (63 ванильных предмета)
     * - basic_foods.json (минимальный набор)
     * - example.json (примеры для пользователей)
     */
}
package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Система превращения испорченных предметов согласно README.
 *
 * Для предметов еды с временной меткой которые не в контейнере создает систему
 * автоматического превращения. При истечении срока заменяет предмет на испорченный вариант:
 * - Мясо и мясные супы -> гнилая плоть
 * - Растительная еда, фрукты, овощи, саженцы, семена -> компост (коричневый гриб)
 * - Выпечка, молочные продукты, зелья -> прогорклое масло (медовая бутылка)
 *
 * Поддерживает все 63 типа предметов еды из Minecraft 1.21.8
 *
 * @author MetaphysicsNecrosis
 * @version 2.0
 * @since 1.21.8
 */
public class SpoilageTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageTransformer.class);

    /**
     * Enum для типов испорченной еды, использует уже существующий TimedFoodManager.SpoiledType
     */
    public enum SpoiledType {
        /** Мясо -> гнилая плоть */
        MEAT("meat", Items.ROTTEN_FLESH),
        /** Растительная еда -> компост (коричневый гриб) */
        PLANT("plant", Items.BROWN_MUSHROOM),
        /** Выпечка -> прогорклое масло (медовая бутылка) */
        BAKERY("bakery", Items.HONEY_BOTTLE);

        private final String name;
        private final Item spoiledItem;

        SpoiledType(String name, Item spoiledItem) {
            this.name = name;
            this.spoiledItem = spoiledItem;
        }

        public String getName() {
            return name;
        }

        public Item getSpoiledItem() {
            return spoiledItem;
        }
    }

    /**
     * Кэш для маппинга предметов к типам превращения
     */
    private static final Map<Item, SpoiledType> TRANSFORMATION_MAPPING = new ConcurrentHashMap<>();

    /**
     * Статистика превращений для отладки
     */
    private static final Map<String, AtomicLong> TRANSFORMATION_STATS = new ConcurrentHashMap<>();

    static {
        initializeTransformationMapping();
    }

    /**
     * Инициализация маппинга предметов к типам превращения
     */
    private static void initializeTransformationMapping() {
        // Мясные продукты -> гнилая плоть
        TRANSFORMATION_MAPPING.put(Items.BEEF, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.PORKCHOP, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.CHICKEN, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.MUTTON, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.RABBIT, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_BEEF, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_PORKCHOP, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_CHICKEN, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_MUTTON, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_RABBIT, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COD, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.SALMON, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.TROPICAL_FISH, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.PUFFERFISH, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_COD, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.COOKED_SALMON, SpoiledType.MEAT);

        // Растительная еда -> компост
        TRANSFORMATION_MAPPING.put(Items.APPLE, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.GOLDEN_APPLE, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.ENCHANTED_GOLDEN_APPLE, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.CARROT, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.POTATO, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.BAKED_POTATO, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.BEETROOT, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.MELON_SLICE, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.SWEET_BERRIES, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.GLOW_BERRIES, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.KELP, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.DRIED_KELP, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.CHORUS_FRUIT, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.POISONOUS_POTATO, SpoiledType.PLANT);

        // Саженцы и растения -> компост
        TRANSFORMATION_MAPPING.put(Items.OAK_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.SPRUCE_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.BIRCH_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.JUNGLE_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.ACACIA_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.DARK_OAK_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.CHERRY_SAPLING, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.AZALEA, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.FLOWERING_AZALEA, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.MANGROVE_PROPAGULE, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.BAMBOO, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.CACTUS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.SUGAR_CANE, SpoiledType.PLANT);

        // Семена -> компост
        TRANSFORMATION_MAPPING.put(Items.WHEAT_SEEDS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.BEETROOT_SEEDS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.MELON_SEEDS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.PUMPKIN_SEEDS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.TORCHFLOWER_SEEDS, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.PITCHER_POD, SpoiledType.PLANT);

        // Выпечка -> прогорклое масло
        TRANSFORMATION_MAPPING.put(Items.BREAD, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.COOKIE, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.CAKE, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.PUMPKIN_PIE, SpoiledType.BAKERY);

        // Супы и жидкая еда -> зависит от основного ингредиента
        TRANSFORMATION_MAPPING.put(Items.MUSHROOM_STEW, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.RABBIT_STEW, SpoiledType.MEAT);
        TRANSFORMATION_MAPPING.put(Items.BEETROOT_SOUP, SpoiledType.PLANT);
        TRANSFORMATION_MAPPING.put(Items.SUSPICIOUS_STEW, SpoiledType.PLANT);

        // Жидкости и особые предметы -> прогорклое масло
        TRANSFORMATION_MAPPING.put(Items.MILK_BUCKET, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.HONEY_BOTTLE, SpoiledType.BAKERY);

        // Зелья -> прогорклое масло (испорченная алхимия)
        TRANSFORMATION_MAPPING.put(Items.POTION, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.SPLASH_POTION, SpoiledType.BAKERY);
        TRANSFORMATION_MAPPING.put(Items.LINGERING_POTION, SpoiledType.BAKERY);

        // Особые предметы -> компост
        TRANSFORMATION_MAPPING.put(Items.SPIDER_EYE, SpoiledType.PLANT);

        LOGGER.info("Инициализирован маппинг превращений для {} предметов", TRANSFORMATION_MAPPING.size());
    }

    /**
     * Основной метод превращения испорченного предмета
     *
     * @param originalStack оригинальный ItemStack
     * @param originalItem оригинальный предмет еды
     * @return превращенный ItemStack
     */
    public static ItemStack transformSpoiledItem(ItemStack originalStack, Item originalItem) {
        if (originalStack.isEmpty() || originalItem == null) {
            LOGGER.warn("Попытка превратить пустой стек или null предмет");
            return ItemStack.EMPTY;
        }

        // Проверяем, что система порчи включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            LOGGER.debug("Система порчи отключена в конфигурации");
            return ItemStack.EMPTY;
        }

        // Не проверяем режим здесь, так как этот метод может вызываться
        // из разных мест, где режим уже проверен (например, из контейнера)

        // Получаем испорченный вариант
        Item spoiledItem = getSpoiledVariant(originalItem);
        if (spoiledItem == Items.AIR) {
            LOGGER.warn("Не найден испорченный вариант для предмета: {}",
                    BuiltInRegistries.ITEM.getKey(originalItem));
            return ItemStack.EMPTY;
        }

        String originalItemId = BuiltInRegistries.ITEM.getKey(originalItem).toString();
        String spoiledItemId = BuiltInRegistries.ITEM.getKey(spoiledItem).toString();
        LOGGER.debug("Найден испорченный вариант для {}: {}", originalItemId, spoiledItemId);

        // Создаем новый стек с сохранением количества
        ItemStack transformedStack = new ItemStack(spoiledItem, originalStack.getCount());

        // Сохраняем enchantments где применимо (для особых предметов)
        if (shouldPreserveEnchantments(originalItem, spoiledItem)) {
            EnchantmentHelper.setEnchantments(transformedStack, EnchantmentHelper.getEnchantmentsForCrafting(originalStack));
        }

        LOGGER.debug("Превращение: {} -> {} (количество: {})",
                originalItemId, spoiledItemId, originalStack.getCount());

        // Обновляем статистику
        TRANSFORMATION_STATS.computeIfAbsent(originalItemId, k -> new AtomicLong(0))
                .addAndGet(originalStack.getCount());

        return transformedStack;
    }

    /**
     * Получает испорченный вариант для оригинального предмета
     *
     * @param originalItem оригинальный предмет еды
     * @return испорченный предмет
     */
    public static Item getSpoiledVariant(Item originalItem) {
        if (originalItem == null) {
            return Items.AIR;
        }

        SpoiledType spoiledType = TRANSFORMATION_MAPPING.get(originalItem);
        if (spoiledType == null) {
            // Если точного маппинга нет, используем автоматическую категоризацию
            spoiledType = getAutomaticSpoiledType(originalItem);

            // Если автоматическая категоризация не сработала, используем логику из TimedFoodManager
            if (spoiledType == null) {
                TimedFoodManager.SpoiledType timedManagerType = TimedFoodManager.getSpoiledType(originalItem);
                spoiledType = convertFromTimedManagerType(timedManagerType);
            }
        }

        return spoiledType != null ? spoiledType.getSpoiledItem() : Items.ROTTEN_FLESH;
    }

    /**
     * Автоматически определяет тип порчи для любого предмета еды по его названию
     *
     * @param item предмет еды
     * @return тип порчи или null если не удалось определить
     */
    public static SpoiledType getAutomaticSpoiledType(Item item) {
        if (item == null) {
            return null;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();

        // Мясо и мясные продукты
        if (itemId.contains("beef") || itemId.contains("pork") || itemId.contains("chicken") ||
            itemId.contains("mutton") || itemId.contains("rabbit") || itemId.contains("meat") ||
            itemId.contains("cod") || itemId.contains("salmon") || itemId.contains("fish") ||
            itemId.contains("bacon") || itemId.contains("ham") || itemId.contains("sausage") ||
            itemId.contains("steak") || itemId.contains("jerky") || itemId.contains("raw")) {
            return SpoiledType.MEAT;
        }

        // Супы на мясной основе
        if ((itemId.contains("stew") || itemId.contains("soup")) &&
            (itemId.contains("rabbit") || itemId.contains("meat") || itemId.contains("beef") ||
             itemId.contains("chicken") || itemId.contains("fish"))) {
            return SpoiledType.MEAT;
        }

        // Молочные продукты, зелья, жидкости
        if (itemId.contains("milk") || itemId.contains("cheese") || itemId.contains("butter") ||
            itemId.contains("cream") || itemId.contains("yogurt") || itemId.contains("honey") ||
            itemId.contains("potion") || itemId.contains("bottle") || itemId.contains("bucket") ||
            itemId.contains("bread") || itemId.contains("cake") || itemId.contains("cookie") ||
            itemId.contains("pie") || itemId.contains("pastry") || itemId.contains("dough") ||
            itemId.contains("flour") || itemId.contains("sugar")) {
            return SpoiledType.BAKERY;
        }

        // Растительная еда (по умолчанию для всего остального)
        if (itemId.contains("apple") || itemId.contains("fruit") || itemId.contains("berry") ||
            itemId.contains("vegetable") || itemId.contains("carrot") || itemId.contains("potato") ||
            itemId.contains("beetroot") || itemId.contains("melon") || itemId.contains("kelp") ||
            itemId.contains("sapling") || itemId.contains("seed") || itemId.contains("plant") ||
            itemId.contains("mushroom") || itemId.contains("fungus") || itemId.contains("leaf") ||
            itemId.contains("flower") || itemId.contains("grass") || itemId.contains("vine") ||
            itemId.contains("root") || itemId.contains("herb") || itemId.contains("spice") ||
            itemId.contains("grain") || itemId.contains("wheat") || itemId.contains("corn") ||
            itemId.contains("rice") || itemId.contains("bean") || itemId.contains("pea") ||
            itemId.contains("nut") || itemId.contains("tomato") || itemId.contains("cucumber") ||
            itemId.contains("onion") || itemId.contains("garlic") || itemId.contains("pepper")) {
            return SpoiledType.PLANT;
        }

        // Если ничего не подошло, но это еда - относим к растительной
        return SpoiledType.PLANT;
    }

    /**
     * Проверяет, включено ли превращение в конфигурации
     *
     * @return true, если превращение включено
     */
    public static boolean isTransformationEnabled() {
        // Проверяем, что система порчи включена и режим - превращение
        return Config.ENABLE_SPOILAGE_SYSTEM.get() &&
               Config.SPOILAGE_MODE.get() == Config.SpoilageMode.TRANSFORM_TO_SPOILED;
    }

    /**
     * Получает тип превращения для предмета
     *
     * @param item предмет еды
     * @return тип превращения (мясо/растение/выпечка)
     */
    public static SpoiledType getTransformationType(Item item) {
        if (item == null) {
            return SpoiledType.MEAT; // по умолчанию
        }

        SpoiledType type = TRANSFORMATION_MAPPING.get(item);
        if (type == null) {
            // Используем логику из TimedFoodManager
            TimedFoodManager.SpoiledType timedManagerType = TimedFoodManager.getSpoiledType(item);
            type = convertFromTimedManagerType(timedManagerType);
        }

        return type != null ? type : SpoiledType.MEAT;
    }

    /**
     * Автоматически превращает предмет, если он испорчен
     * Основной метод для интеграции с существующими системами
     *
     * @param itemStack стек предметов для проверки и превращения
     * @return превращенный стек или оригинальный, если превращение не нужно
     */
    public static ItemStack autoTransformIfSpoiled(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return itemStack;
        }

        // Проверяем, что превращение включено
        if (!isTransformationEnabled()) {
            return itemStack;
        }

        // Проверяем, имеет ли предмет временную метку
        if (!TimedFoodManager.isTimedFood(itemStack)) {
            return itemStack; // нет временной метки, не превращаем
        }

        // Проверяем, может ли предмет превращаться
        if (!canItemBeTransformed(itemStack.getItem())) {
            return itemStack;
        }

        // Здесь мы не проверяем порчу сами, а полагаемся на вызывающую систему
        // которая уже определила, что предмет испорчен
        return transformSpoiledItem(itemStack, itemStack.getItem());
    }

    /**
     * Проверяет, может ли предмет быть превращен
     *
     * @param item предмет для проверки
     * @return true, если предмет может быть превращен
     */
    public static boolean canItemBeTransformed(Item item) {
        if (item == null) {
            return false;
        }

        // Проверяем, есть ли прямой маппинг для этого предмета
        if (TRANSFORMATION_MAPPING.containsKey(item)) {
            return true;
        }

        // Проверяем через TimedFoodManager
        if (TimedFoodManager.canSpoil(item)) {
            return true;
        }

        // УНИВЕРСАЛЬНАЯ ПРОВЕРКА: если у предмета есть FOOD компонент - он может портиться!
        return isUniversalFoodItem(item);
    }

    /**
     * Универсальная проверка: является ли предмет едой (имеет FOOD компонент)
     *
     * @param item предмет для проверки
     * @return true, если предмет является едой
     */
    public static boolean isUniversalFoodItem(Item item) {
        if (item == null) {
            return false;
        }

        try {
            // Проверяем, есть ли у предмета FoodProperties (FOOD компонент)
            return item.components().has(net.minecraft.core.component.DataComponents.FOOD);
        } catch (Exception e) {
            // Если что-то пошло не так, возвращаем false
            LOGGER.debug("Ошибка при проверке FOOD компонента для предмета {}: {}",
                BuiltInRegistries.ITEM.getKey(item), e.getMessage());
            return false;
        }
    }

    /**
     * Конвертирует тип из TimedFoodManager в наш тип
     */
    private static SpoiledType convertFromTimedManagerType(TimedFoodManager.SpoiledType timedType) {
        if (timedType == null) {
            return null;
        }

        return switch (timedType) {
            case ROTTEN_FLESH -> SpoiledType.MEAT;
            case COMPOST -> SpoiledType.PLANT;
            case RANCID_OIL -> SpoiledType.BAKERY;
        };
    }

    /**
     * Проверяет, нужно ли сохранять enchantments при превращении
     */
    private static boolean shouldPreserveEnchantments(Item originalItem, Item spoiledItem) {
        // Пока что не сохраняем enchantments, так как испорченные предметы
        // обычно не должны иметь зачарований
        // В будущем можно добавить особую логику для специальных предметов
        return false;
    }

    /**
     * Получает статистику превращений для отладки
     *
     * @return Map с количеством превращений по типам предметов
     */
    public static Map<String, Long> getTransformationStatistics() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        TRANSFORMATION_STATS.forEach((key, value) -> stats.put(key, value.get()));
        return stats;
    }

    /**
     * Сбрасывает статистику превращений
     */
    public static void resetStatistics() {
        TRANSFORMATION_STATS.clear();
        LOGGER.info("Статистика превращений SpoilageTransformer сброшена");
    }

    /**
     * Генерирует отчет о превращениях
     *
     * @return отформатированная строка с отчетом
     */
    public static String generateTransformationReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== ОТЧЕТ ПРЕВРАЩЕНИЙ ===\n");

        Map<String, Long> stats = getTransformationStatistics();
        if (stats.isEmpty()) {
            report.append("Превращений не было выполнено\n");
        } else {
            report.append(String.format("Всего типов превращенных предметов: %d\n", stats.size()));

            long totalTransformed = stats.values().stream().mapToLong(Long::longValue).sum();
            report.append(String.format("Общее количество превращенных предметов: %d\n\n", totalTransformed));

            report.append("Детализация по предметам:\n");
            stats.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> report.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue())));
        }

        report.append("\n");
        report.append(String.format("Статус превращения: %s\n",
                isTransformationEnabled() ? "ВКЛЮЧЕНО" : "ОТКЛЮЧЕНО"));
        report.append(String.format("Поддерживаемых типов предметов: %d\n", TRANSFORMATION_MAPPING.size()));
        report.append("==========================");

        return report.toString();
    }

    /**
     * Валидирует корректность работы трансформера
     *
     * @return true, если все проверки прошли успешно
     */
    public static boolean validateTransformer() {
        try {
            // Проверяем инициализацию маппинга
            if (TRANSFORMATION_MAPPING.isEmpty()) {
                LOGGER.error("Маппинг превращений не инициализирован");
                return false;
            }

            // Проверяем базовые превращения
            Item testItem = Items.APPLE;
            Item spoiledVariant = getSpoiledVariant(testItem);
            if (spoiledVariant == Items.AIR) {
                LOGGER.error("Не удалось получить испорченный вариант для тестового предмета");
                return false;
            }

            // Проверяем тип превращения
            SpoiledType transformationType = getTransformationType(testItem);
            if (transformationType == null) {
                LOGGER.error("Не удалось получить тип превращения для тестового предмета");
                return false;
            }

            // Проверяем создание превращенного стека
            ItemStack originalStack = new ItemStack(testItem, 5);
            ItemStack transformedStack = transformSpoiledItem(originalStack, testItem);

            if (transformedStack.isEmpty()) {
                LOGGER.error("Не удалось создать превращенный стек");
                return false;
            }

            if (transformedStack.getCount() != originalStack.getCount()) {
                LOGGER.error("Количество предметов не сохранилось при превращении");
                return false;
            }

            LOGGER.info("Валидация SpoilageTransformer прошла успешно");
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка при валидации SpoilageTransformer", e);
            return false;
        }
    }

    /**
     * Очищает все кэши трансформера
     */
    public static void clearCache() {
        TRANSFORMATION_MAPPING.clear();
        TRANSFORMATION_STATS.clear();
        initializeTransformationMapping();
        LOGGER.info("Кэш SpoilageTransformer очищен и переинициализирован");
    }

    /**
     * Получает информацию о кэше для отладки
     *
     * @return информация о размере кэшей
     */
    public static String getCacheInfo() {
        return String.format("SpoilageTransformer - Маппинг: %d, Статистика: %d",
                TRANSFORMATION_MAPPING.size(), TRANSFORMATION_STATS.size());
    }
}
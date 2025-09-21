package com.metaphysicsnecrosis.metaphysicsspoilage.manager;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageTransformer;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.performance.PerformanceManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Менеджер для виртуального управления предметами с порчей в NeoForge 1.21.8.
 * Предоставляет систему создания ItemStack с временными метками на лету,
 * обработку порчи еды и интеграцию с существующими системами.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class TimedFoodManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimedFoodManager.class);

    /**
     * Enum для типов испорченной еды согласно README
     */
    public enum SpoiledType {
        /** Гнилая плоть для мясных продуктов */
        ROTTEN_FLESH("rotten_flesh", Items.ROTTEN_FLESH),
        /** Компост для растительной еды */
        COMPOST("compost", Items.BROWN_MUSHROOM), // Временно используем коричневый гриб как компост
        /** Прогорклое масло для выпечки */
        RANCID_OIL("rancid_oil", Items.HONEY_BOTTLE); // Временно используем мед как прогорклое масло

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
     * Оптимизированный кэш для производительности - хранит соответствие предметов типам испорченной еды
     * Использует более эффективную структуру данных для быстрого доступа
     */
    private static final Map<Item, SpoiledType> SPOILED_TYPE_CACHE = new ConcurrentHashMap<>(128, 0.75f, 4);

    /**
     * Кэш для оригинальных предметов еды с предварительно заданным размером
     */
    private static final Map<Item, Item> ORIGINAL_FOOD_CACHE = new ConcurrentHashMap<>(64, 0.75f, 4);

    /**
     * FIFO кэш для проверки возможности порчи предметов (ограничен 512 записями)
     */
    private static final Map<String, Boolean> CAN_SPOIL_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Boolean>(256, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 512;
            }
        }
    );

    /**
     * FIFO кэш времени срока годности для предметов (ограничен 256 записями)
     */
    private static final Map<String, Long> SPOILAGE_TIME_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Long>(128, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > 256;
            }
        }
    );

    static {
        initializeSpoiledTypeMapping();
    }

    /**
     * Инициализация маппинга предметов к типам испорченной еды
     */
    private static void initializeSpoiledTypeMapping() {
        // Мясные продукты -> гнилая плоть
        SPOILED_TYPE_CACHE.put(Items.BEEF, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.PORKCHOP, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.CHICKEN, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.MUTTON, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.RABBIT, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_BEEF, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_PORKCHOP, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_CHICKEN, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_MUTTON, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_RABBIT, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COD, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.SALMON, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.TROPICAL_FISH, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.PUFFERFISH, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_COD, SpoiledType.ROTTEN_FLESH);
        SPOILED_TYPE_CACHE.put(Items.COOKED_SALMON, SpoiledType.ROTTEN_FLESH);

        // Растительная еда -> компост
        SPOILED_TYPE_CACHE.put(Items.APPLE, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.CARROT, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.POTATO, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.BAKED_POTATO, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.BEETROOT, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.MELON_SLICE, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.SWEET_BERRIES, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.GLOW_BERRIES, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.KELP, SpoiledType.COMPOST);
        SPOILED_TYPE_CACHE.put(Items.DRIED_KELP, SpoiledType.COMPOST);

        // Выпечка -> прогорклое масло
        SPOILED_TYPE_CACHE.put(Items.BREAD, SpoiledType.RANCID_OIL);
        SPOILED_TYPE_CACHE.put(Items.COOKIE, SpoiledType.RANCID_OIL);
        SPOILED_TYPE_CACHE.put(Items.CAKE, SpoiledType.RANCID_OIL);
        SPOILED_TYPE_CACHE.put(Items.PUMPKIN_PIE, SpoiledType.RANCID_OIL);

        LOGGER.info("Инициализирован маппинг типов испорченной еды для {} предметов", SPOILED_TYPE_CACHE.size());
    }

    /**
     * Создает ItemStack еды с временной меткой (ОПТИМИЗИРОВАННАЯ ВЕРСИЯ)
     *
     * @param baseItem базовый предмет еды
     * @param creationDay день создания предмета
     * @return ItemStack с установленной временной меткой
     */
    public static ItemStack createTimedFood(Item baseItem, long creationDay) {
        try (var profiler = PerformanceManager.profile("TimedFoodManager.createTimedFood")) {
            if (baseItem == null) {
                LOGGER.warn("Попытка создать временную еду с null предметом");
                return ItemStack.EMPTY;
            }

            // Кэшированная проверка системы порчи
            Boolean systemEnabled = PerformanceManager.getFromCacheOrCompute(
                "system_enabled", Config.ENABLE_SPOILAGE_SYSTEM::get);

            if (!systemEnabled) {
                String itemId = BuiltInRegistries.ITEM.getKey(baseItem).toString();
                LOGGER.debug("Система порчи отключена, возвращаем обычный предмет: {}", itemId);
                return new ItemStack(baseItem);
            }

            String itemId = BuiltInRegistries.ITEM.getKey(baseItem).toString();

            // Кэшированная проверка исключений
            Boolean isExcluded = PerformanceManager.getFromCacheOrCompute(
                "excluded_" + itemId, () -> Config.isItemExcluded(itemId));

            if (isExcluded) {
                LOGGER.debug("Предмет {} исключен из системы порчи", itemId);
                return new ItemStack(baseItem);
            }

            // Кэшированная проверка возможности порчи
            Boolean canSpoil = CAN_SPOIL_CACHE.computeIfAbsent(itemId, k -> SpoilageUtils.canItemSpoil(baseItem));

            if (!canSpoil) {
                return new ItemStack(baseItem);
            }

            ItemStack stack = new ItemStack(baseItem);
            SpoilageUtils.setCreationDay(stack, creationDay);

            LOGGER.debug("Создан временный предмет {} с днем создания {}", itemId, creationDay);
            return stack;
        }
    }

    /**
     * Обрабатывает предмет еды, добавляя временную метку на лету
     *
     * @param original оригинальный ItemStack
     * @param level серверный уровень
     * @return обработанный ItemStack с временной меткой
     */
    public static ItemStack processFood(ItemStack original, ServerLevel level) {
        if (original.isEmpty()) {
            return original;
        }

        // Если уже есть временная метка, ничего не делаем
        if (SpoilageUtils.hasTimestamp(original)) {
            LOGGER.debug("Предмет {} уже имеет временную метку",
                    BuiltInRegistries.ITEM.getKey(original.getItem()));
            return original;
        }

        // Автоматически устанавливаем временную метку
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long currentDay = tracker.getCurrentDay();

        ItemStack processed = original.copy();
        SpoilageUtils.autoSetTimestamp(processed, level);

        String itemId = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        LOGGER.debug("Обработан предмет {} с установкой временной метки на день {}", itemId, currentDay);

        return processed;
    }

    /**
     * Проверяет, является ли предмет временной едой
     *
     * @param stack ItemStack для проверки
     * @return true, если предмет имеет временную метку
     */
    public static boolean isTimedFood(ItemStack stack) {
        return SpoilageUtils.hasTimestamp(stack);
    }

    /**
     * Получает оригинальный предмет еды из временного стека
     *
     * @param timedStack временный ItemStack
     * @return оригинальный предмет еды
     */
    public static Item getOriginalFood(ItemStack timedStack) {
        if (timedStack.isEmpty()) {
            return Items.AIR;
        }

        // В данной реализации оригинальный предмет - это тот же предмет
        // но без компонента временной метки
        return timedStack.getItem();
    }

    /**
     * Проверяет и обрабатывает порчу предмета
     *
     * @param stack ItemStack для проверки
     * @param level серверный уровень
     * @return обработанный ItemStack (может быть испорченным или пустым)
     */
    public static ItemStack checkAndProcessSpoilage(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) {
            return stack;
        }

        // Проверяем, испорчен ли предмет
        if (!SpoilageUtils.isItemSpoiled(stack, level)) {
            return stack; // Предмет еще свежий
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Config.SpoilageMode mode = Config.SPOILAGE_MODE.get();

        LOGGER.debug("Обрабатывается порча предмета {} в режиме {}", itemId, mode.getName());

        switch (mode) {
            case INSTANT_DISAPPEAR:
                // Предмет исчезает мгновенно
                LOGGER.debug("Предмет {} исчез из-за порчи", itemId);
                return ItemStack.EMPTY;

            case TRANSFORM_TO_SPOILED:
                // Используем новую систему превращения SpoilageTransformer
                ItemStack transformedStack = SpoilageTransformer.transformSpoiledItem(stack, stack.getItem());
                if (!transformedStack.isEmpty()) {
                    LOGGER.debug("Предмет {} превращен в испорченный через SpoilageTransformer: {}",
                            itemId, BuiltInRegistries.ITEM.getKey(transformedStack.getItem()));
                    return transformedStack;
                } else {
                    // Fallback на старую систему, если новая не сработала
                    Item spoiledItem = getSpoiledItem(stack.getItem());
                    if (spoiledItem != Items.AIR) {
                        ItemStack spoiledStack = new ItemStack(spoiledItem, stack.getCount());
                        LOGGER.debug("Предмет {} превращен в испорченный (fallback): {}",
                                itemId, BuiltInRegistries.ITEM.getKey(spoiledItem));
                        return spoiledStack;
                    } else {
                        LOGGER.warn("Не удалось найти испорченный вариант для {}, предмет исчезнет", itemId);
                        return ItemStack.EMPTY;
                    }
                }

            default:
                LOGGER.warn("Неизвестный режим порчи: {}", mode);
                return ItemStack.EMPTY;
        }
    }

    /**
     * Получает испорченный предмет для данного типа еды
     *
     * @param originalItem оригинальный предмет еды
     * @return испорченный предмет
     */
    private static Item getSpoiledItem(Item originalItem) {
        SpoiledType spoiledType = SPOILED_TYPE_CACHE.getOrDefault(originalItem, SpoiledType.ROTTEN_FLESH);
        return spoiledType.getSpoiledItem();
    }

    /**
     * Получает тип испорченной еды для предмета
     *
     * @param item предмет еды
     * @return тип испорченной еды
     */
    public static SpoiledType getSpoiledType(Item item) {
        return SPOILED_TYPE_CACHE.getOrDefault(item, SpoiledType.ROTTEN_FLESH);
    }

    /**
     * Регистрирует кастомный маппинг предмета к типу испорченной еды
     *
     * @param item предмет еды
     * @param spoiledType тип испорченной еды
     */
    public static void registerSpoiledType(Item item, SpoiledType spoiledType) {
        SPOILED_TYPE_CACHE.put(item, spoiledType);
        LOGGER.debug("Зарегистрирован маппинг {} -> {}",
                BuiltInRegistries.ITEM.getKey(item), spoiledType.getName());
    }

    /**
     * Получает количество дней до порчи для предмета
     *
     * @param stack ItemStack для проверки
     * @param level серверный уровень
     * @return количество дней до порчи, или -1 если предмет не портится
     */
    public static long getDaysUntilSpoilage(ItemStack stack, ServerLevel level) {
        return SpoilageUtils.getDaysUntilSpoilage(stack, level);
    }

    /**
     * Проверяет, может ли предмет портиться согласно конфигурации
     *
     * @param item предмет для проверки
     * @return true, если предмет может портиться
     */
    public static boolean canSpoil(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        // Проверяем глобальное включение системы
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return false;
        }

        // Проверяем исключения
        if (Config.isItemExcluded(itemId)) {
            return false;
        }

        // Проверяем базовую способность к порче
        return SpoilageUtils.canItemSpoil(item);
    }

    /**
     * Получает эффективную длительность хранения с учетом множителя скорости
     * ОБНОВЛЕНО: теперь использует JsonSpoilageConfig вместо hardcoded настроек
     *
     * @param item предмет еды
     * @return эффективная длительность в днях
     */
    public static double getEffectiveSpoilageDuration(Item item) {
        // Получаем время порчи из JSON конфигурации или универсальной системы
        long baseDuration = SpoilageUtils.getSpoilageTime(item);
        if (baseDuration <= 0) return baseDuration;

        // Применяем множитель скорости
        return baseDuration / Config.SPOILAGE_SPEED_MULTIPLIER.get();
    }

    /**
     * Очищает кэш (для тестирования и перезагрузки конфигурации) - ОПТИМИЗИРОВАННАЯ ВЕРСИЯ
     */
    public static void clearCache() {
        SPOILED_TYPE_CACHE.clear();
        ORIGINAL_FOOD_CACHE.clear();
        CAN_SPOIL_CACHE.clear();
        SPOILAGE_TIME_CACHE.clear();
        initializeSpoiledTypeMapping();
    }

    /**
     * Получает расширенную статистику кэша для отладки - УЛУЧШЕННАЯ ВЕРСИЯ
     *
     * @return информация о размере всех кэшей
     */
    public static String getCacheStats() {
        return String.format("TimedFoodManager кэши - SpoiledType: %d, OriginalFood: %d, CanSpoil: %d, SpoilageTime: %d",
                SPOILED_TYPE_CACHE.size(), ORIGINAL_FOOD_CACHE.size(),
                CAN_SPOIL_CACHE.size(), SPOILAGE_TIME_CACHE.size());
    }

    /**
     * Оптимизированная проверка возможности порчи с кэшированием
     */
    public static boolean canSpoilOptimized(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        return CAN_SPOIL_CACHE.computeIfAbsent(itemId, k -> {
            // Проверяем глобальное включение системы
            if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
                return false;
            }

            // Проверяем исключения
            if (Config.isItemExcluded(itemId)) {
                return false;
            }

            // Проверяем базовую способность к порче
            return SpoilageUtils.canItemSpoil(item);
        });
    }

    /**
     * Оптимизированное получение времени до порчи с кэшированием
     */
    public static long getSpoilageTimeOptimized(Item item) {
        try (var profiler = PerformanceManager.profile("TimedFoodManager.getSpoilageTimeOptimized")) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            return SPOILAGE_TIME_CACHE.computeIfAbsent(itemId, k -> SpoilageUtils.getSpoilageTime(item));
        }
    }

    /**
     * Пакетная обработка предметов для оптимизации (НОВЫЙ МЕТОД)
     */
    public static void batchProcessItems(java.util.List<ItemStack> items, ServerLevel level) {
        try (var profiler = PerformanceManager.profile("TimedFoodManager.batchProcessItems")) {
            if (items == null || items.isEmpty()) {
                return;
            }

            long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();

            // Группируем предметы по типам для оптимизации
            java.util.Map<Item, java.util.List<ItemStack>> groupedItems = new java.util.HashMap<>();
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    groupedItems.computeIfAbsent(stack.getItem(), k -> new java.util.ArrayList<>()).add(stack);
                }
            }

            // Обрабатываем группы предметов
            for (java.util.Map.Entry<Item, java.util.List<ItemStack>> entry : groupedItems.entrySet()) {
                Item item = entry.getKey();
                java.util.List<ItemStack> stacks = entry.getValue();

                // Проверяем возможность порчи один раз для всей группы
                if (canSpoilOptimized(item)) {
                    for (ItemStack stack : stacks) {
                        if (!SpoilageUtils.hasTimestamp(stack)) {
                            SpoilageUtils.setCreationDay(stack, currentDay);
                        }
                    }
                }
            }

            LOGGER.debug("Пакетная обработка {} предметов в {} группах",
                        items.size(), groupedItems.size());
        }
    }

    /**
     * Прогревает кэши для улучшения производительности (НОВЫЙ МЕТОД)
     */
    public static void warmupCaches() {
        try (var profiler = PerformanceManager.profile("TimedFoodManager.warmupCaches")) {
            LOGGER.info("Начинается прогрев кэшей TimedFoodManager...");

            // Прогреваем кэш для основных типов еды
            var commonFoodItems = java.util.List.of(
                net.minecraft.world.item.Items.APPLE,
                net.minecraft.world.item.Items.BREAD,
                net.minecraft.world.item.Items.COOKED_BEEF,
                net.minecraft.world.item.Items.COOKED_PORKCHOP,
                net.minecraft.world.item.Items.COOKED_CHICKEN,
                net.minecraft.world.item.Items.POTATO,
                net.minecraft.world.item.Items.CARROT
            );

            for (Item item : commonFoodItems) {
                canSpoilOptimized(item);
                getSpoilageTimeOptimized(item);
            }

            LOGGER.info("Прогрев кэшей завершен. Состояние: {}", getCacheStats());
        }
    }

    /**
     * Очищает устаревшие записи кэша (НОВЫЙ МЕТОД)
     */
    public static void cleanupStaleCacheEntries() {
        try (var profiler = PerformanceManager.profile("TimedFoodManager.cleanupStaleCacheEntries")) {
            // Логика очистки может быть добавлена позже при необходимости
            // Пока что это заглушка для будущего использования
            LOGGER.debug("Очистка устаревших записей кэша (заглушка)");
        }
    }

    /**
     * Валидирует корректность работы менеджера
     *
     * @return true, если менеджер работает корректно
     */
    public static boolean validateManager() {
        try {
            // Проверяем инициализацию кэша
            if (SPOILED_TYPE_CACHE.isEmpty()) {
                LOGGER.error("Кэш типов испорченной еды не инициализирован");
                return false;
            }

            // Проверяем основные функции
            ItemStack testStack = createTimedFood(Items.APPLE, 1L);
            if (testStack.isEmpty()) {
                LOGGER.error("Не удалось создать тестовый временный предмет");
                return false;
            }

            if (!isTimedFood(testStack)) {
                LOGGER.error("Созданный предмет не определяется как временный");
                return false;
            }

            LOGGER.info("Валидация TimedFoodManager прошла успешно");
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации TimedFoodManager", e);
            return false;
        }
    }
}
package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Главный класс для проверки срока годности и логики порчи предметов.
 *
 * Предоставляет комплексную систему проверки порчи с поддержкой:
 * - Индивидуальной проверки предметов
 * - Массовой обработки контейнеров и инвентарей
 * - Кэширования результатов для производительности
 * - Статистики и логирования
 * - Интеграции с существующими системами
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class SpoilageChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageChecker.class);

    // === КЭШИРОВАНИЕ ДЛЯ ПРОИЗВОДИТЕЛЬНОСТИ ===

    /**
     * Кэш результатов проверки порчи для предотвращения повторных вычислений.
     * Ключ: строка вида "itemId:creationDay:currentDay"
     * Значение: результат проверки (true = испорчен, false = свежий)
     */
    private static final Map<String, Boolean> SPOILAGE_CHECK_CACHE = new ConcurrentHashMap<>();

    /**
     * Кэш времени до порчи для оптимизации tooltip'ов и UI.
     * Ключ: строка вида "itemId:creationDay:currentDay"
     * Значение: количество дней до порчи
     */
    private static final Map<String, Long> TIME_UNTIL_SPOILAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * Время последней очистки кэша для периодической очистки устаревших данных
     */
    private static volatile long lastCacheCleanup = System.currentTimeMillis();

    /**
     * Интервал очистки кэша в миллисекундах (5 минут)
     */
    private static final long CACHE_CLEANUP_INTERVAL = 5 * 60 * 1000L;

    // === СТАТИСТИКА ===

    /**
     * Статистика работы системы порчи
     */
    public static class SpoilageStatistics {
        private final AtomicLong totalChecks = new AtomicLong(0);
        private final AtomicLong spoiledItems = new AtomicLong(0);
        private final AtomicLong containerChecks = new AtomicLong(0);
        private final AtomicLong inventoryChecks = new AtomicLong(0);
        private final Map<String, AtomicLong> spoiledByType = new ConcurrentHashMap<>();
        private final AtomicLong cacheHits = new AtomicLong(0);
        private final AtomicLong cacheMisses = new AtomicLong(0);

        public long getTotalChecks() { return totalChecks.get(); }
        public long getSpoiledItems() { return spoiledItems.get(); }
        public long getContainerChecks() { return containerChecks.get(); }
        public long getInventoryChecks() { return inventoryChecks.get(); }
        public Map<String, Long> getSpoiledByType() {
            Map<String, Long> result = new HashMap<>();
            spoiledByType.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
        public long getCacheHits() { return cacheHits.get(); }
        public long getCacheMisses() { return cacheMisses.get(); }
        public double getCacheHitRatio() {
            long hits = cacheHits.get();
            long misses = cacheMisses.get();
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        void incrementTotalChecks() { totalChecks.incrementAndGet(); }
        void incrementSpoiledItems() { spoiledItems.incrementAndGet(); }
        void incrementContainerChecks() { containerChecks.incrementAndGet(); }
        void incrementInventoryChecks() { inventoryChecks.incrementAndGet(); }
        void incrementSpoiledByType(String itemType) {
            spoiledByType.computeIfAbsent(itemType, k -> new AtomicLong(0)).incrementAndGet();
        }
        void incrementCacheHits() { cacheHits.incrementAndGet(); }
        void incrementCacheMisses() { cacheMisses.incrementAndGet(); }

        public void reset() {
            totalChecks.set(0);
            spoiledItems.set(0);
            containerChecks.set(0);
            inventoryChecks.set(0);
            spoiledByType.clear();
            cacheHits.set(0);
            cacheMisses.set(0);
        }
    }

    private static final SpoilageStatistics STATISTICS = new SpoilageStatistics();

    // === ОГРАНИЧЕНИЕ ЧАСТОТЫ ПРОВЕРОК ===

    /**
     * Карта последних проверок для игроков (предотвращает спам проверок)
     */
    private static final Map<UUID, Long> LAST_PLAYER_CHECK = new ConcurrentHashMap<>();

    /**
     * Минимальный интервал между проверками инвентаря игрока в миллисекундах (1 секунда)
     */
    private static final long PLAYER_CHECK_COOLDOWN = 1000L;

    // === ОСНОВНЫЕ МЕТОДЫ ПРОВЕРКИ ===

    /**
     * Проверяет, испорчен ли предмет на основе его временной метки и текущего игрового дня.
     *
     * @param itemStack Стек предметов для проверки
     * @param level Серверный уровень для получения текущего дня
     * @return true, если предмет испорчен и должен быть удален/заменен
     */
    public static boolean isItemSpoiled(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return false;
        }

        STATISTICS.incrementTotalChecks();

        // Проверяем, включена ли система порчи
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return false;
        }

        // Используем существующий метод из SpoilageUtils
        boolean result = SpoilageUtils.isItemSpoiled(itemStack, level);

        if (result) {
            STATISTICS.incrementSpoiledItems();
            String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
            STATISTICS.incrementSpoiledByType(itemId);

            LOGGER.debug("Предмет {} признан испорченным на день {}",
                    itemId, WorldDayTracker.getInstance(level).getCurrentDay());
        }

        return result;
    }

    /**
     * Получает количество дней до порчи предмета.
     *
     * @param itemStack Стек предметов для проверки
     * @param level Серверный уровень
     * @return Количество дней до порчи, или -1 если предмет не портится
     */
    public static long getTimeUntilSpoilage(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return -1;
        }

        // Проверяем кэш
        String cacheKey = createCacheKey(itemStack, level);
        Long cachedResult = TIME_UNTIL_SPOILAGE_CACHE.get(cacheKey);
        if (cachedResult != null) {
            STATISTICS.incrementCacheHits();
            return cachedResult;
        }

        STATISTICS.incrementCacheMisses();

        // Вычисляем время до порчи
        long result = SpoilageUtils.getDaysUntilSpoilage(itemStack, level);

        // Кэшируем результат
        TIME_UNTIL_SPOILAGE_CACHE.put(cacheKey, result);

        return result;
    }

    /**
     * Обрабатывает порчу предмета согласно настройкам конфигурации.
     *
     * @param itemStack Стек предметов для обработки
     * @param level Серверный уровень
     * @return Обработанный ItemStack (может быть испорченным вариантом или пустым)
     */
    public static ItemStack processSpoilage(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return itemStack;
        }

        // Проверяем, нужно ли обрабатывать порчу
        if (!isItemSpoiled(itemStack, level)) {
            return itemStack; // Предмет еще свежий
        }

        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        LOGGER.debug("Обрабатывается порча предмета: {}", itemId);

        // Используем TimedFoodManager для обработки (который теперь интегрирован с SpoilageTransformer)
        return TimedFoodManager.checkAndProcessSpoilage(itemStack, level);
    }

    // === МАССОВАЯ ОБРАБОТКА ===

    /**
     * Проверяет весь контейнер на предмет испорченных предметов и удаляет/заменяет их.
     *
     * @param container Контейнер для проверки
     * @param level Серверный уровень
     */
    public static void checkContainerForSpoilage(Container container, ServerLevel level) {
        if (container == null || level == null) {
            return;
        }

        STATISTICS.incrementContainerChecks();

        // Проверяем временные ограничения для предотвращения спама
        cleanupCacheIfNeeded();

        int spoiledCount = 0;
        int totalItems = 0;

        // Обрабатываем все слоты контейнера
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            totalItems++;

            // Обрабатываем порчу предмета
            ItemStack processed = processSpoilage(stack, level);

            // Если предмет изменился, обновляем контейнер
            if (!ItemStack.matches(stack, processed)) {
                container.setItem(i, processed);
                spoiledCount++;

                if (processed.isEmpty()) {
                    LOGGER.debug("Удален испорченный предмет из слота {} контейнера", i);
                } else {
                    LOGGER.debug("Заменен испорченный предмет в слоте {} контейнера", i);
                }
            }
        }

        if (spoiledCount > 0) {
            LOGGER.info("Обработано контейнеров: 1, испорченных предметов: {} из {} общих",
                    spoiledCount, totalItems);
        }
    }

    /**
     * Проверяет инвентарь игрока на предмет испорченных предметов.
     *
     * @param player Игрок для проверки
     * @param level Серверный уровень
     */
    public static void checkPlayerInventory(Player player, ServerLevel level) {
        if (player == null || level == null) {
            return;
        }

        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();

        // Проверяем кулдаун для предотвращения частых проверок
        Long lastCheck = LAST_PLAYER_CHECK.get(playerId);
        if (lastCheck != null && (currentTime - lastCheck) < PLAYER_CHECK_COOLDOWN) {
            return; // Слишком рано для новой проверки
        }

        LAST_PLAYER_CHECK.put(playerId, currentTime);
        STATISTICS.incrementInventoryChecks();

        Inventory inventory = player.getInventory();
        int spoiledCount = 0;
        int totalItems = 0;

        // Проверяем основной инвентарь
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            totalItems++;

            // Если это FoodContainer, проверяем его содержимое
            if (stack.getItem() instanceof com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer) {
                com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer.checkAndRemoveSpoiledFood(stack, level);
                // Обновляем предмет в инвентаре после проверки порчи
                inventory.setItem(i, stack);
            } else {
                // Обычная проверка для остальных предметов
                ItemStack processed = processSpoilage(stack, level);

                if (!ItemStack.matches(stack, processed)) {
                    inventory.setItem(i, processed);
                    spoiledCount++;
                }
            }
        }

        if (spoiledCount > 0) {
            LOGGER.info("Проверен инвентарь игрока {}: испорченных предметов {} из {}",
                    player.getName().getString(), spoiledCount, totalItems);
        }
    }

    /**
     * Пакетная обработка множественных контейнеров для оптимизации производительности.
     *
     * @param containers Список контейнеров для проверки
     * @param level Серверный уровень
     */
    public static void checkMultipleContainers(List<Container> containers, ServerLevel level) {
        if (containers == null || containers.isEmpty() || level == null) {
            return;
        }

        LOGGER.debug("Начинается пакетная проверка {} контейнеров", containers.size());

        long startTime = System.currentTimeMillis();
        int totalSpoiled = 0;
        int processedContainers = 0;

        for (Container container : containers) {
            if (container != null) {
                int beforeCount = getTotalItems(container);
                checkContainerForSpoilage(container, level);
                int afterCount = getTotalItems(container);

                totalSpoiled += (beforeCount - afterCount);
                processedContainers++;
            }
        }

        long endTime = System.currentTimeMillis();
        LOGGER.info("Пакетная обработка завершена: {} контейнеров, {} испорченных предметов за {} мс",
                processedContainers, totalSpoiled, (endTime - startTime));
    }

    // === УТИЛИТНЫЕ МЕТОДЫ ===

    /**
     * Создает ключ для кэширования результатов проверки
     */
    private static String createCacheKey(ItemStack itemStack, ServerLevel level) {
        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        long creationDay = SpoilageUtils.getCreationDay(itemStack);
        long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();
        return itemId + ":" + creationDay + ":" + currentDay;
    }

    /**
     * Подсчитывает общее количество предметов в контейнере
     */
    private static int getTotalItems(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Очищает устаревшие записи кэша при необходимости
     */
    private static void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_CLEANUP_INTERVAL) {
            clearCache();
            lastCacheCleanup = currentTime;
            LOGGER.debug("Выполнена автоматическая очистка кэша SpoilageChecker");
        }
    }

    // === ПУБЛИЧНЫЕ УТИЛИТНЫЕ МЕТОДЫ ===

    /**
     * Очищает все кэши для освобождения памяти и обновления данных.
     */
    public static void clearCache() {
        SPOILAGE_CHECK_CACHE.clear();
        TIME_UNTIL_SPOILAGE_CACHE.clear();
        LAST_PLAYER_CHECK.clear();
        LOGGER.info("Кэш SpoilageChecker очищен");
    }

    /**
     * Получает статистику работы системы порчи.
     *
     * @return Объект со статистикой
     */
    public static SpoilageStatistics getStatistics() {
        return STATISTICS;
    }

    /**
     * Сбрасывает статистику работы системы.
     */
    public static void resetStatistics() {
        STATISTICS.reset();
        LOGGER.info("Статистика SpoilageChecker сброшена");
    }

    /**
     * Получает информацию о состоянии кэшей для отладки.
     *
     * @return Строка с информацией о размерах кэшей
     */
    public static String getCacheInfo() {
        return String.format("SpoilageChecker кэши - Проверки: %d, Время: %d, Игроки: %d",
                SPOILAGE_CHECK_CACHE.size(),
                TIME_UNTIL_SPOILAGE_CACHE.size(),
                LAST_PLAYER_CHECK.size());
    }

    /**
     * Выполняет валидацию корректности работы SpoilageChecker.
     *
     * @param level Серверный уровень для тестирования
     * @return true, если все проверки прошли успешно
     */
    public static boolean validate(ServerLevel level) {
        try {
            // Создаем тестовый предмет с временной меткой
            ItemStack testStack = TimedFoodManager.createTimedFood(
                    net.minecraft.world.item.Items.APPLE,
                    WorldDayTracker.getInstance(level).getCurrentDay()
            );

            if (testStack.isEmpty()) {
                LOGGER.error("Не удалось создать тестовый предмет для валидации");
                return false;
            }

            // Проверяем основные функции
            boolean spoiled = isItemSpoiled(testStack, level);
            long timeUntil = getTimeUntilSpoilage(testStack, level);
            ItemStack processed = processSpoilage(testStack, level);

            // Базовые проверки
            if (timeUntil < 0 && SpoilageUtils.canItemSpoil(testStack.getItem())) {
                LOGGER.error("Некорректное время до порчи для портящегося предмета");
                return false;
            }

            LOGGER.info("Валидация SpoilageChecker прошла успешно");
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка при валидации SpoilageChecker", e);
            return false;
        }
    }

    /**
     * Генерирует подробный отчет о работе системы порчи.
     *
     * @return Отформатированная строка с отчетом
     */
    public static String generateReport() {
        SpoilageStatistics stats = getStatistics();
        StringBuilder report = new StringBuilder();

        report.append("=== ОТЧЕТ СИСТЕМЫ ПОРЧИ ===\n");
        report.append(String.format("Общих проверок: %d\n", stats.getTotalChecks()));
        report.append(String.format("Испорченных предметов: %d\n", stats.getSpoiledItems()));
        report.append(String.format("Проверок контейнеров: %d\n", stats.getContainerChecks()));
        report.append(String.format("Проверок инвентарей: %d\n", stats.getInventoryChecks()));
        report.append(String.format("Эффективность кэша: %.2f%%\n", stats.getCacheHitRatio() * 100));

        report.append("\n=== СТАТИСТИКА ПО ТИПАМ ===\n");
        Map<String, Long> spoiledByType = stats.getSpoiledByType();
        if (spoiledByType.isEmpty()) {
            report.append("Нет данных по типам предметов\n");
        } else {
            spoiledByType.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> report.append(String.format("%s: %d\n", entry.getKey(), entry.getValue())));
        }

        report.append(String.format("\n%s\n", getCacheInfo()));
        report.append("=============================");

        return report.toString();
    }
}
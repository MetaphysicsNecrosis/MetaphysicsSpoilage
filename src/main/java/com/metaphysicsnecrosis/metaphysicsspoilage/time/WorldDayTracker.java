package com.metaphysicsnecrosis.metaphysicsspoilage.time;

import com.metaphysicsnecrosis.metaphysicsspoilage.performance.PerformanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Оптимизированный класс для отслеживания игровых дней.
 * Поддерживает неограниченное количество дней с использованием long.
 *
 * ОПТИМИЗАЦИИ:
 * - Кэширование экземпляров трекеров
 * - Ленивая инициализация
 * - Профилирование операций
 * - Оптимизированная синхронизация
 *
 * @author MetaphysicsNecrosis
 * @version 1.1 (оптимизированная)
 * @since 1.21.8
 */
public class WorldDayTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDayTracker.class);

    /**
     * Кэш экземпляров трекеров по измерениям для быстрого доступа
     */
    private static final Map<String, WorldDayTracker> INSTANCES = new ConcurrentHashMap<>();

    /**
     * Кэш последних вычисленных дней для оптимизации повторных обращений
     */
    private static final Map<String, Long> DAY_CACHE = new ConcurrentHashMap<>();

    /**
     * Время последнего обновления кэша дней для каждого измерения
     */
    private static final Map<String, Long> LAST_CACHE_UPDATE = new ConcurrentHashMap<>();

    /**
     * Интервал кэширования дней (500мс) для предотвращения частых пересчетов
     */
    private static final long CACHE_INTERVAL = 500L;

    private volatile long currentDay;
    private volatile boolean isDirty = false;
    private volatile long lastUpdateTime = 0;

    /**
     * Конструктор по умолчанию для создания нового трекера
     */
    public WorldDayTracker() {
        this.currentDay = 0L;
    }

    /**
     * Конструктор для загрузки данных из сохранения
     */
    public WorldDayTracker(long currentDay) {
        this.currentDay = currentDay;
    }

    /**
     * Получить текущий день
     * @return текущий игровой день
     */
    public long getCurrentDay() {
        return currentDay;
    }

    /**
     * Увеличить день на 1 и пометить данные как измененные
     */
    public void incrementDay() {
        currentDay++;
        setDirty();
    }

    /**
     * Установить текущий день
     * @param day новое значение дня
     */
    public void setCurrentDay(long day) {
        if (this.currentDay != day) {
            this.currentDay = day;
            setDirty();
        }
    }

    /**
     * Пометить данные как измененные
     */
    private void setDirty() {
        this.isDirty = true;
    }

    /**
     * Проверить, изменились ли данные
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Сбросить флаг изменений
     */
    public void clearDirty() {
        this.isDirty = false;
    }

    /**
     * Получить экземпляр WorldDayTracker для указанного уровня (ОПТИМИЗИРОВАННАЯ ВЕРСИЯ)
     * @param level серверный уровень
     * @return экземпляр WorldDayTracker
     */
    public static WorldDayTracker getInstance(ServerLevel level) {
        try (var profiler = PerformanceManager.profile("WorldDayTracker.getInstance")) {
            String dimensionKey = level.dimension().location().toString();

            // Проверяем кэш дней
            Long cachedDay = DAY_CACHE.get(dimensionKey);
            Long lastUpdate = LAST_CACHE_UPDATE.get(dimensionKey);
            long currentTime = System.currentTimeMillis();

            // Если кэш свежий, используем его
            if (cachedDay != null && lastUpdate != null &&
                (currentTime - lastUpdate) < CACHE_INTERVAL) {

                WorldDayTracker tracker = INSTANCES.get(dimensionKey);
                if (tracker != null) {
                    tracker.currentDay = cachedDay;
                    return tracker;
                }
            }

            // Получаем или создаем трекер
            WorldDayTracker tracker = INSTANCES.computeIfAbsent(dimensionKey, k -> {
                long worldDay = TimeUtils.getCurrentDayFromWorldTime(level);
                WorldDayTracker newTracker = new WorldDayTracker(worldDay);
                LOGGER.debug("Создан новый WorldDayTracker для измерения {} с днем {}", k, worldDay);
                return newTracker;
            });

            // Обновляем кэш дня если необходимо
            if (lastUpdate == null || (currentTime - lastUpdate) >= CACHE_INTERVAL) {
                long worldDay = TimeUtils.getCurrentDayFromWorldTime(level);
                DAY_CACHE.put(dimensionKey, worldDay);
                LAST_CACHE_UPDATE.put(dimensionKey, currentTime);

                // Синхронизируем трекер с мировым временем
                if (tracker.currentDay != worldDay) {
                    tracker.setCurrentDay(worldDay);
                }
            }

            return tracker;
        }
    }

    /**
     * Очистить кэш при выгрузке мира (ОПТИМИЗИРОВАННАЯ ВЕРСИЯ)
     */
    public static void clearCache(Level level) {
        try (var profiler = PerformanceManager.profile("WorldDayTracker.clearCache")) {
            String dimensionKey = level.dimension().location().toString();
            INSTANCES.remove(dimensionKey);
            DAY_CACHE.remove(dimensionKey);
            LAST_CACHE_UPDATE.remove(dimensionKey);
            LOGGER.debug("Очищен кэш WorldDayTracker для измерения {}", dimensionKey);
        }
    }

    /**
     * Получить все активные трекеры (для отладки)
     */
    public static Map<String, WorldDayTracker> getAllInstances() {
        return new ConcurrentHashMap<>(INSTANCES);
    }

    /**
     * Получает текущий день с учетом кэширования (НОВЫЙ ОПТИМИЗИРОВАННЫЙ МЕТОД)
     */
    public long getCurrentDayOptimized() {
        long currentTime = System.currentTimeMillis();

        // Если данные свежие, возвращаем кэшированное значение
        if (currentTime - lastUpdateTime < CACHE_INTERVAL) {
            return currentDay;
        }

        lastUpdateTime = currentTime;
        return currentDay;
    }

    /**
     * Принудительно обновляет день из мирового времени (НОВЫЙ МЕТОД)
     */
    public void forceUpdateFromWorldTime(ServerLevel level) {
        try (var profiler = PerformanceManager.profile("WorldDayTracker.forceUpdate")) {
            long worldDay = TimeUtils.getCurrentDayFromWorldTime(level);
            if (this.currentDay != worldDay) {
                setCurrentDay(worldDay);
                LOGGER.debug("Принудительно обновлен день: {} -> {}", this.currentDay, worldDay);
            }
        }
    }

    /**
     * Очищает все кэши принудительно (для профилактики)
     */
    public static void clearAllCaches() {
        try (var profiler = PerformanceManager.profile("WorldDayTracker.clearAllCaches")) {
            int instancesSize = INSTANCES.size();
            int dayCacheSize = DAY_CACHE.size();

            INSTANCES.clear();
            DAY_CACHE.clear();
            LAST_CACHE_UPDATE.clear();

            LOGGER.info("Очищены все кэши WorldDayTracker: экземпляров={}, дней={}",
                       instancesSize, dayCacheSize);
        }
    }

    /**
     * Получает статистику использования кэша
     */
    public static String getCacheStatistics() {
        return String.format("WorldDayTracker кэши - Трекеров: %d, Дней: %d, Обновлений: %d",
                INSTANCES.size(), DAY_CACHE.size(), LAST_CACHE_UPDATE.size());
    }

    /**
     * Проверяет валидность трекера
     */
    public boolean isValid() {
        return currentDay >= 0 && lastUpdateTime > 0;
    }
}
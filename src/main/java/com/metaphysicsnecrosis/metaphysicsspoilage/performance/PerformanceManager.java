package com.metaphysicsnecrosis.metaphysicsspoilage.performance;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Map;

/**
 * Менеджер производительности для мониторинга и оптимизации работы мода MetaphysicsSpoilage.
 *
 * Обеспечивает:
 * - Профилирование горячих точек
 * - Кэширование для оптимизации повторных вычислений
 * - Мониторинг использования памяти
 * - Статистика производительности
 * - Автоматическую оптимизацию
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class PerformanceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceManager.class);

    // === ПРОФИЛИРОВАНИЕ ===

    /**
     * Карта для хранения времени выполнения различных операций
     */
    private static final Map<String, LongAdder> OPERATION_TIMES = new ConcurrentHashMap<>();

    /**
     * Карта для подсчета количества вызовов операций
     */
    private static final Map<String, LongAdder> OPERATION_COUNTS = new ConcurrentHashMap<>();

    /**
     * Карта для хранения максимального времени выполнения операций
     */
    private static final Map<String, AtomicLong> MAX_OPERATION_TIMES = new ConcurrentHashMap<>();

    // === КЭШИРОВАНИЕ ===

    /**
     * Универсальный кэш для результатов вычислений
     */
    private static final Map<String, Object> PERFORMANCE_CACHE = new ConcurrentHashMap<>();

    /**
     * Времена последнего доступа к кэшу для автоматической очистки
     */
    private static final Map<String, Long> CACHE_ACCESS_TIMES = new ConcurrentHashMap<>();

    /**
     * Статистика кэша
     */
    private static final LongAdder CACHE_HITS = new LongAdder();
    private static final LongAdder CACHE_MISSES = new LongAdder();

    // === НАСТРОЙКИ ===

    /**
     * Максимальное время жизни записей в кэше (5 минут)
     */
    private static final long CACHE_TTL = 5 * 60 * 1000L;

    /**
     * Интервал очистки кэша (10 минут)
     */
    private static final long CACHE_CLEANUP_INTERVAL = 10 * 60 * 1000L;

    /**
     * Время последней очистки кэша
     */
    private static volatile long lastCacheCleanup = System.currentTimeMillis();

    /**
     * Пороговое значение для отчета о медленных операциях (1мс)
     */
    private static final long SLOW_OPERATION_THRESHOLD = 1_000_000L; // наносекунды

    // === ПРОФИЛИРОВАНИЕ ОПЕРАЦИЙ ===

    /**
     * Класс для профилирования выполнения операций
     */
    public static class OperationProfiler implements AutoCloseable {
        private final String operationName;
        private final long startTime;

        private OperationProfiler(String operationName) {
            this.operationName = operationName;
            this.startTime = System.nanoTime();
        }

        @Override
        public void close() {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            recordOperationTime(operationName, duration);
        }
    }

    /**
     * Начинает профилирование операции
     *
     * @param operationName Название операции
     * @return Профайлер для использования в try-with-resources
     */
    public static OperationProfiler profile(String operationName) {
        return new OperationProfiler(operationName);
    }

    /**
     * Записывает время выполнения операции
     *
     * @param operationName Название операции
     * @param durationNanos Время выполнения в наносекундах
     */
    private static void recordOperationTime(String operationName, long durationNanos) {
        OPERATION_TIMES.computeIfAbsent(operationName, k -> new LongAdder()).add(durationNanos);
        OPERATION_COUNTS.computeIfAbsent(operationName, k -> new LongAdder()).increment();

        // Обновляем максимальное время
        MAX_OPERATION_TIMES.computeIfAbsent(operationName, k -> new AtomicLong(0))
                .updateAndGet(current -> Math.max(current, durationNanos));

        // Логируем медленные операции
        if (durationNanos > SLOW_OPERATION_THRESHOLD) {
            LOGGER.warn("Медленная операция '{}': {}мс",
                    operationName, String.format("%.2f", durationNanos / 1_000_000.0));
        }
    }

    // === КЭШИРОВАНИЕ ===

    /**
     * Получает значение из кэша или вычисляет его
     *
     * @param key Ключ кэша
     * @param supplier Поставщик значения при отсутствии в кэше
     * @param <T> Тип значения
     * @return Значение из кэша или вычисленное
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFromCacheOrCompute(String key, java.util.function.Supplier<T> supplier) {
        cleanupCacheIfNeeded();

        Object cached = PERFORMANCE_CACHE.get(key);
        if (cached != null) {
            CACHE_ACCESS_TIMES.put(key, System.currentTimeMillis());
            CACHE_HITS.increment();
            return (T) cached;
        }

        CACHE_MISSES.increment();
        T computed = supplier.get();
        if (computed != null) {
            PERFORMANCE_CACHE.put(key, computed);
            CACHE_ACCESS_TIMES.put(key, System.currentTimeMillis());
        }

        return computed;
    }

    /**
     * Помещает значение в кэш
     *
     * @param key Ключ
     * @param value Значение
     */
    public static void putInCache(String key, Object value) {
        cleanupCacheIfNeeded();
        PERFORMANCE_CACHE.put(key, value);
        CACHE_ACCESS_TIMES.put(key, System.currentTimeMillis());
    }

    /**
     * Удаляет значение из кэша
     *
     * @param key Ключ
     */
    public static void removeFromCache(String key) {
        PERFORMANCE_CACHE.remove(key);
        CACHE_ACCESS_TIMES.remove(key);
    }

    /**
     * Очищает весь кэш
     */
    public static void clearCache() {
        PERFORMANCE_CACHE.clear();
        CACHE_ACCESS_TIMES.clear();
        lastCacheCleanup = System.currentTimeMillis();
        LOGGER.debug("Кэш PerformanceManager очищен");
    }

    /**
     * Очищает устаревшие записи из кэша при необходимости
     */
    private static void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_CLEANUP_INTERVAL) {
            cleanupExpiredCacheEntries();
            lastCacheCleanup = currentTime;
        }
    }

    /**
     * Очищает просроченные записи из кэша
     */
    private static void cleanupExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        var iterator = CACHE_ACCESS_TIMES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (currentTime - entry.getValue() > CACHE_TTL) {
                String key = entry.getKey();
                iterator.remove();
                PERFORMANCE_CACHE.remove(key);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            LOGGER.debug("Удалено {} просроченных записей из кэша", removedCount);
        }
    }

    // === СТАТИСТИКА И ОТЧЕТЫ ===

    /**
     * Получает среднее время выполнения операции
     *
     * @param operationName Название операции
     * @return Среднее время в миллисекундах
     */
    public static double getAverageOperationTime(String operationName) {
        LongAdder totalTime = OPERATION_TIMES.get(operationName);
        LongAdder count = OPERATION_COUNTS.get(operationName);

        if (totalTime == null || count == null || count.longValue() == 0) {
            return 0.0;
        }

        return (totalTime.doubleValue() / count.longValue()) / 1_000_000.0; // в миллисекундах
    }

    /**
     * Получает максимальное время выполнения операции
     *
     * @param operationName Название операции
     * @return Максимальное время в миллисекундах
     */
    public static double getMaxOperationTime(String operationName) {
        AtomicLong maxTime = MAX_OPERATION_TIMES.get(operationName);
        return maxTime != null ? maxTime.get() / 1_000_000.0 : 0.0;
    }

    /**
     * Получает количество вызовов операции
     *
     * @param operationName Название операции
     * @return Количество вызовов
     */
    public static long getOperationCount(String operationName) {
        LongAdder count = OPERATION_COUNTS.get(operationName);
        return count != null ? count.longValue() : 0;
    }

    /**
     * Получает коэффициент попаданий в кэш
     *
     * @return Коэффициент от 0.0 до 1.0
     */
    public static double getCacheHitRatio() {
        long hits = CACHE_HITS.longValue();
        long misses = CACHE_MISSES.longValue();
        long total = hits + misses;

        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Генерирует отчет о производительности
     *
     * @return Отформатированный отчет
     */
    public static String generatePerformanceReport() {
        StringBuilder report = new StringBuilder();

        report.append("=== ОТЧЕТ О ПРОИЗВОДИТЕЛЬНОСТИ ===\n");
        report.append(String.format("Кэш: размер=%d, попадания=%.2f%%, hits=%d, misses=%d\n",
                PERFORMANCE_CACHE.size(),
                getCacheHitRatio() * 100,
                CACHE_HITS.longValue(),
                CACHE_MISSES.longValue()));

        report.append("\n=== ПРОФИЛИРОВАНИЕ ОПЕРАЦИЙ ===\n");

        if (OPERATION_COUNTS.isEmpty()) {
            report.append("Нет данных по операциям\n");
        } else {
            OPERATION_COUNTS.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().longValue(), e1.getValue().longValue()))
                    .forEach(entry -> {
                        String operation = entry.getKey();
                        long count = entry.getValue().longValue();
                        double avgTime = getAverageOperationTime(operation);
                        double maxTime = getMaxOperationTime(operation);

                        report.append(String.format("%-30s: вызовов=%d, сред=%.2fмс, макс=%.2fмс\n",
                                operation, count, avgTime, maxTime));
                    });
        }

        report.append("\n=== ИСПОЛЬЗОВАНИЕ ПАМЯТИ ===\n");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        report.append(String.format("Использовано: %.2f МБ / %.2f МБ (%.1f%%)\n",
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                (double) usedMemory / maxMemory * 100));

        report.append("===================================");

        return report.toString();
    }

    /**
     * Сбрасывает всю статистику
     */
    public static void resetStatistics() {
        OPERATION_TIMES.clear();
        OPERATION_COUNTS.clear();
        MAX_OPERATION_TIMES.clear();
        CACHE_HITS.reset();
        CACHE_MISSES.reset();
        clearCache();

        LOGGER.info("Статистика PerformanceManager сброшена");
    }

    /**
     * Получает статистику по горячим точкам (топ-5 самых частых операций)
     *
     * @return Массив названий операций, отсортированных по частоте
     */
    public static String[] getHotspots() {
        return OPERATION_COUNTS.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().longValue(), e1.getValue().longValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    /**
     * Проверяет, включено ли профилирование производительности
     *
     * @return true если профилирование включено
     */
    public static boolean isProfilingEnabled() {
        return Config.ENABLE_PERFORMANCE_PROFILING.get();
    }

    /**
     * Запускает принудительную сборку мусора (только для отладки)
     */
    public static void forceGarbageCollection() {
        if (isProfilingEnabled()) {
            System.gc();
            LOGGER.debug("Принудительная сборка мусора выполнена");
        }
    }

    /**
     * Логирует отчет о производительности
     */
    public static void logPerformanceReport() {
        if (isProfilingEnabled()) {
            String report = generatePerformanceReport();
            LOGGER.info("Отчет о производительности:\n{}", report);
        }
    }
}
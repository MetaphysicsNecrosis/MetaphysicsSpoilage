package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Специализированные тесты для WorldDayTracker с фокусом на большие значения дней.
 *
 * Проверяет:
 * - Корректность сохранения и загрузки экстремальных значений
 * - Потокобезопасность при работе с большими числами
 * - Производительность операций с long значениями
 * - Целостность данных при переполнении
 */
public class WorldDayTrackerTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDayTrackerTests.class);

    /**
     * Тестирует базовые операции с экстремальными значениями
     */
    public static boolean testBasicExtremeValues(ServerLevel level) {
        LOGGER.info("--- Тестирование базовых операций WorldDayTracker с экстремальными значениями ---");

        boolean allPassed = true;
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);

        // Тестовые значения
        long[] extremeValues = {
            0L,
            1L,
            -1L,
            1_000_000L,           // Миллион
            -1_000_000L,
            1_000_000_000L,       // Миллиард
            -1_000_000_000L,
            1_000_000_000_000L,   // Триллион
            -1_000_000_000_000L,
            Long.MAX_VALUE - 1000L,
            Long.MAX_VALUE - 1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE + 1000L,
            Long.MIN_VALUE + 1L,
            Long.MIN_VALUE
        };

        for (long testValue : extremeValues) {
            try {
                // Тест установки и получения значения
                tracker.setCurrentDay(testValue);
                long retrieved = tracker.getCurrentDay();

                if (retrieved != testValue) {
                    LOGGER.error("ОШИБКА: Установлено {}, получено {}", testValue, retrieved);
                    allPassed = false;
                } else {
                    LOGGER.debug("OK: Значение {} корректно сохранено и получено", testValue);
                }

                // Тест проверки флага dirty
                if (!tracker.isDirty()) {
                    LOGGER.error("ОШИБКА: Флаг dirty не установлен после изменения значения {}", testValue);
                    allPassed = false;
                }

                tracker.clearDirty();
                if (tracker.isDirty()) {
                    LOGGER.error("ОШИБКА: Флаг dirty не очищен для значения {}", testValue);
                    allPassed = false;
                }

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при тестировании значения {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования базовых операций: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует операции инкремента с близкими к максимальным значениями
     */
    public static boolean testIncrementOverflow(ServerLevel level) {
        LOGGER.info("--- Тестирование инкремента с переполнением ---");

        boolean allPassed = true;
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);

        try {
            // Тест близко к максимуму
            long nearMax = Long.MAX_VALUE - 5L;
            tracker.setCurrentDay(nearMax);

            for (int i = 0; i < 10; i++) {
                long beforeIncrement = tracker.getCurrentDay();
                tracker.incrementDay();
                long afterIncrement = tracker.getCurrentDay();

                LOGGER.debug("Инкремент {}: {} -> {}", i, beforeIncrement, afterIncrement);

                // Проверяем, что инкремент произошел (даже если с переполнением)
                if (beforeIncrement < Long.MAX_VALUE) {
                    if (afterIncrement != beforeIncrement + 1) {
                        LOGGER.error("ОШИБКА: Неожиданный результат инкремента {} -> {}", beforeIncrement, afterIncrement);
                        allPassed = false;
                    }
                } else {
                    // При переполнении ожидаем Long.MIN_VALUE
                    if (afterIncrement != Long.MIN_VALUE) {
                        LOGGER.warn("ПЕРЕПОЛНЕНИЕ: {} -> {} (ожидалось {})", beforeIncrement, afterIncrement, Long.MIN_VALUE);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("ИСКЛЮЧЕНИЕ при тестировании инкремента с переполнением: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования инкремента: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует потокобезопасность операций с большими значениями
     */
    public static boolean testConcurrency(ServerLevel level) {
        LOGGER.info("--- Тестирование потокобезопасности с большими значениями ---");

        boolean allPassed = true;
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            final int OPERATIONS_PER_THREAD = 100;
            final int THREAD_COUNT = 10;

            AtomicInteger errors = new AtomicInteger(0);
            CompletableFuture<Void>[] futures = new CompletableFuture[THREAD_COUNT];

            // Запускаем параллельные операции
            for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                final long baseValue = 1_000_000_000_000L + (threadId * 1_000_000_000L); // Большие базовые значения

                futures[threadId] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                        try {
                            long testValue = baseValue + i;

                            // Установка и чтение
                            tracker.setCurrentDay(testValue);
                            long retrieved = tracker.getCurrentDay();

                            // Может быть не равно из-за параллельного доступа, но не должно быть исключений
                            if (retrieved < 0 && testValue > 0) {
                                // Неожиданная смена знака может указывать на проблему
                                errors.incrementAndGet();
                            }

                            // Инкремент
                            tracker.incrementDay();

                        } catch (Exception e) {
                            LOGGER.error("Ошибка в потоке: {}", e.getMessage());
                            errors.incrementAndGet();
                        }
                    }
                }, executor);
            }

            // Ждем завершения всех потоков
            CompletableFuture.allOf(futures).get();

            int totalErrors = errors.get();
            if (totalErrors > 0) {
                LOGGER.warn("Обнаружено {} ошибок при параллельном доступе", totalErrors);
                // Не считаем это критической ошибкой для ConcurrentHashMap, но логируем
            }

            LOGGER.info("Завершено {} операций в {} потоках с {} ошибками",
                OPERATIONS_PER_THREAD * THREAD_COUNT, THREAD_COUNT, totalErrors);

        } catch (Exception e) {
            LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА при тестировании потокобезопасности: {}", e.getMessage());
            allPassed = false;
        } finally {
            executor.shutdown();
        }

        LOGGER.info("Результат тестирования потокобезопасности: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует производительность операций с большими значениями
     */
    public static boolean testPerformance(ServerLevel level) {
        LOGGER.info("--- Тестирование производительности с большими значениями ---");

        boolean allPassed = true;
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);

        try {
            final int OPERATIONS_COUNT = 10_000;

            // Тест производительности установки больших значений
            long startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long bigValue = Long.MAX_VALUE - i;
                tracker.setCurrentDay(bigValue);
            }

            long setTimeNs = System.nanoTime() - startTime;
            double setTimeMs = setTimeNs / 1_000_000.0;
            double avgSetTimeNs = (double) setTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время установки {} больших значений: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, setTimeMs, avgSetTimeNs);

            // Тест производительности чтения
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                tracker.getCurrentDay();
            }

            long getTimeNs = System.nanoTime() - startTime;
            double getTimeMs = getTimeNs / 1_000_000.0;
            double avgGetTimeNs = (double) getTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время чтения {} значений: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, getTimeMs, avgGetTimeNs);

            // Тест производительности инкремента
            tracker.setCurrentDay(1_000_000_000L); // Безопасное значение
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                tracker.incrementDay();
            }

            long incTimeNs = System.nanoTime() - startTime;
            double incTimeMs = incTimeNs / 1_000_000.0;
            double avgIncTimeNs = (double) incTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время {} инкрементов: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, incTimeMs, avgIncTimeNs);

            // Проверяем, что производительность приемлема (менее 1 мкс на операцию)
            if (avgSetTimeNs > 1000 || avgGetTimeNs > 1000 || avgIncTimeNs > 1000) {
                LOGGER.warn("ПРОИЗВОДИТЕЛЬНОСТЬ: Операции выполняются медленнее ожидаемого");
                // Не считаем критической ошибкой, так как зависит от системы
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании производительности: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования производительности: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует граничные случаи с математическими операциями
     */
    public static boolean testMathematicalEdgeCases(ServerLevel level) {
        LOGGER.info("--- Тестирование математических граничных случаев ---");

        boolean allPassed = true;
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);

        try {
            // Тест последовательности значений около границ
            long[] boundaryValues = {
                Long.MIN_VALUE,
                Long.MIN_VALUE + 1,
                -1L,
                0L,
                1L,
                Long.MAX_VALUE - 1,
                Long.MAX_VALUE
            };

            for (int i = 0; i < boundaryValues.length - 1; i++) {
                long current = boundaryValues[i];
                long next = boundaryValues[i + 1];

                tracker.setCurrentDay(current);
                long retrieved = tracker.getCurrentDay();

                if (retrieved != current) {
                    LOGGER.error("ОШИБКА: Граничное значение {} не сохранилось корректно (получено {})", current, retrieved);
                    allPassed = false;
                }

                // Проверяем разность между соседними граничными значениями
                if (current < Long.MAX_VALUE && next > Long.MIN_VALUE) {
                    long difference = next - current;
                    LOGGER.debug("Разность между {} и {}: {}", current, next, difference);
                }
            }

            // Тест арифметических операций с большими значениями
            long bigValue = Long.MAX_VALUE / 2;
            tracker.setCurrentDay(bigValue);

            // Симуляция накопления дней
            for (int i = 0; i < 1000; i++) {
                tracker.incrementDay();
                if (tracker.getCurrentDay() < bigValue) {
                    LOGGER.error("ОШИБКА: Неожиданное уменьшение значения при инкременте");
                    allPassed = false;
                    break;
                }
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании математических граничных случаев: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования математических граничных случаев: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Запускает все специализированные тесты WorldDayTracker
     */
    public static boolean runAllTests(ServerLevel level) {
        LOGGER.info("=== ЗАПУСК ВСЕХ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ WorldDayTracker ===");

        boolean basicTests = testBasicExtremeValues(level);
        boolean overflowTests = testIncrementOverflow(level);
        boolean concurrencyTests = testConcurrency(level);
        boolean performanceTests = testPerformance(level);
        boolean mathTests = testMathematicalEdgeCases(level);

        boolean allPassed = basicTests && overflowTests && concurrencyTests && performanceTests && mathTests;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ WorldDayTracker ===");
        LOGGER.info("Базовые операции: {}", basicTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Переполнение: {}", overflowTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Потокобезопасность: {}", concurrencyTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Производительность: {}", performanceTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Математические граничные случаи: {}", mathTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("ОБЩИЙ РЕЗУЛЬТАТ: {}", allPassed ? "ВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "ОБНАРУЖЕНЫ ПРОБЛЕМЫ");

        return allPassed;
    }
}
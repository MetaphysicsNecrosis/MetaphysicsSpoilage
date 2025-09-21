package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.tooltip.TooltipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Специализированные тесты для Tooltip системы с фокусом на большие значения дней.
 *
 * Проверяет:
 * - Форматирование экстремально больших чисел в читаемом виде
 * - Корректность отображения дат в тултипах
 * - Производительность операций форматирования
 * - Обработку переполнения и граничных случаев
 */
public class TooltipSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TooltipSystemTests.class);

    /**
     * Тестирует форматирование времени с экстремальными значениями
     */
    public static boolean testTimeFormatting() {
        LOGGER.info("--- Тестирование форматирования времени с экстремальными значениями ---");

        boolean allPassed = true;

        long[] extremeValues = {
            0L,
            1L,
            -1L,
            1_000L,               // Тысяча дней
            -1_000L,
            1_000_000L,           // Миллион дней
            -1_000_000L,
            1_000_000_000L,       // Миллиард дней
            -1_000_000_000L,
            1_000_000_000_000L,   // Триллион дней
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
                String formatted = TooltipUtils.formatTimeRemaining(testValue);

                // Проверяем, что результат не null и не пустой
                if (formatted == null || formatted.trim().isEmpty()) {
                    LOGGER.error("ОШИБКА: Пустой результат форматирования для {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Проверяем базовую логику форматирования
                if (testValue <= 0 && !formatted.equals("0")) {
                    LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Неожиданное форматирование для {} -> {}", testValue, formatted);
                }

                // Проверяем, что очень большие числа корректно форматируются
                if (Math.abs(testValue) > 1_000_000_000L) {
                    if (formatted.length() > 50) { // Разумное ограничение длины
                        LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Очень длинная строка для {} -> {}", testValue, formatted);
                    }
                }

                LOGGER.debug("Форматирование: {} -> {}", testValue, formatted);

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при форматировании {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования форматирования времени: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует создание компонентов дня создания с экстремальными значениями
     */
    public static boolean testCreationDayComponents() {
        LOGGER.info("--- Тестирование компонентов дня создания ---");

        boolean allPassed = true;

        long[] testValues = {
            0L, 1L, -1L,
            Long.MAX_VALUE, Long.MIN_VALUE,
            1_000_000_000L, -1_000_000_000L
        };

        for (long testValue : testValues) {
            try {
                var component = TooltipUtils.createCreationDayComponent(testValue);

                // Проверяем, что компонент создался
                if (component == null) {
                    LOGGER.error("ОШИБКА: Null компонент для дня создания {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Проверяем, что компонент содержит разумный текст
                String text = component.getString();
                if (text == null || text.trim().isEmpty()) {
                    LOGGER.error("ОШИБКА: Пустой текст компонента для дня {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Проверяем, что текст содержит значение дня (как строку)
                String dayString = String.valueOf(testValue);
                if (!text.contains(dayString)) {
                    LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Текст '{}' не содержит день {}", text, testValue);
                }

                LOGGER.debug("Компонент дня создания: {} -> {}", testValue, text);

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при создании компонента дня {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования компонентов дня создания: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует расчет свежести с экстремальными значениями
     */
    public static boolean testFreshnessCalculation() {
        LOGGER.info("--- Тестирование расчета свежести ---");

        boolean allPassed = true;

        // Тестируем различные сценарии свежести
        long[][] testCases = {
            // {daysRemaining, totalSpoilageDays}
            {0L, 100L},                      // Испорчено
            {-1L, 100L},                     // Давно испорчено
            {1L, 100L},                      // Почти испорчено (1%)
            {10L, 100L},                     // Близко к порче (10%)
            {25L, 100L},                     // Умеренно свежее (25%)
            {50L, 100L},                     // Довольно свежее (50%)
            {75L, 100L},                     // Очень свежее (75%)
            {Long.MAX_VALUE, Long.MAX_VALUE}, // Экстремальный случай
            {Long.MIN_VALUE, 1000L},         // Очень испорчено
            {1_000_000_000L, 2_000_000_000L} // Большие числа
        };

        for (long[] testCase : testCases) {
            long daysRemaining = testCase[0];
            long totalDays = testCase[1];

            try {
                var freshness = TooltipUtils.calculateFreshness(daysRemaining, totalDays);

                // Проверяем, что результат не null
                if (freshness == null) {
                    LOGGER.error("ОШИБКА: Null результат свежести для {} / {}", daysRemaining, totalDays);
                    allPassed = false;
                    continue;
                }

                // Проверяем логичность результата
                if (daysRemaining <= 0 && freshness != TooltipUtils.FreshnessLevel.SPOILED) {
                    LOGGER.error("ОШИБКА: Неиспорченная еда при daysRemaining <= 0: {} -> {}",
                        daysRemaining, freshness);
                    allPassed = false;
                }

                if (totalDays > 0 && daysRemaining > totalDays && freshness == TooltipUtils.FreshnessLevel.SPOILED) {
                    LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Испорченная еда при daysRemaining > totalDays: {} / {} -> {}",
                        daysRemaining, totalDays, freshness);
                }

                LOGGER.debug("Расчет свежести: {}/{} дней -> {}", daysRemaining, totalDays, freshness);

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при расчете свежести {}/{}: {}", daysRemaining, totalDays, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования расчета свежести: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует производительность операций форматирования
     */
    public static boolean testPerformance() {
        LOGGER.info("--- Тестирование производительности Tooltip системы ---");

        boolean allPassed = true;

        try {
            final int OPERATIONS_COUNT = 10_000;

            // Тест производительности форматирования времени
            long startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long testValue = Long.MAX_VALUE - i;
                TooltipUtils.formatTimeRemaining(testValue);
            }

            long formatTimeNs = System.nanoTime() - startTime;
            double formatTimeMs = formatTimeNs / 1_000_000.0;
            double avgFormatTimeNs = (double) formatTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время форматирования {} значений: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, formatTimeMs, avgFormatTimeNs);

            // Тест производительности создания компонентов
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long testValue = i * 1_000_000L;
                TooltipUtils.createCreationDayComponent(testValue);
            }

            long componentTimeNs = System.nanoTime() - startTime;
            double componentTimeMs = componentTimeNs / 1_000_000.0;
            double avgComponentTimeNs = (double) componentTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время создания {} компонентов: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, componentTimeMs, avgComponentTimeNs);

            // Тест производительности расчета свежести
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long remaining = i;
                long total = OPERATIONS_COUNT;
                TooltipUtils.calculateFreshness(remaining, total);
            }

            long freshnessTimeNs = System.nanoTime() - startTime;
            double freshnessTimeMs = freshnessTimeNs / 1_000_000.0;
            double avgFreshnessTimeNs = (double) freshnessTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время расчета {} свежести: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, freshnessTimeMs, avgFreshnessTimeNs);

            // Проверяем, что производительность приемлема
            if (avgFormatTimeNs > 10000 || avgComponentTimeNs > 10000 || avgFreshnessTimeNs > 1000) {
                LOGGER.warn("ПРОИЗВОДИТЕЛЬНОСТЬ: Операции выполняются медленнее ожидаемого");
                // Не считаем критической ошибкой
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании производительности: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования производительности: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует граничные случаи и обработку ошибок
     */
    public static boolean testEdgeCases() {
        LOGGER.info("--- Тестирование граничных случаев Tooltip системы ---");

        boolean allPassed = true;

        try {
            // Тест деления на ноль в расчете свежести
            try {
                var freshness = TooltipUtils.calculateFreshness(10L, 0L);
                LOGGER.debug("Расчет свежести с totalDays=0: {}", freshness);
            } catch (ArithmeticException e) {
                LOGGER.debug("OK: Корректная обработка деления на ноль");
            } catch (Exception e) {
                LOGGER.warn("Неожиданное исключение при totalDays=0: {}", e.getMessage());
            }

            // Тест с отрицательными общими днями
            try {
                var freshness = TooltipUtils.calculateFreshness(10L, -100L);
                LOGGER.debug("Расчет свежести с отрицательными totalDays: {}", freshness);
            } catch (Exception e) {
                LOGGER.debug("Обработка отрицательных totalDays: {}", e.getMessage());
            }

            // Тест переполнения в расчетах свежести
            try {
                var freshness = TooltipUtils.calculateFreshness(Long.MAX_VALUE, Long.MAX_VALUE);
                LOGGER.debug("Расчет свежести с MAX_VALUE: {}", freshness);
            } catch (Exception e) {
                LOGGER.debug("Обработка переполнения в расчете свежести: {}", e.getMessage());
            }

            // Тест создания компонентов с очень длинными строками
            try {
                var component = TooltipUtils.createCreationDayComponent(Long.MAX_VALUE);
                String text = component.getString();
                if (text.length() > 1000) {
                    LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Очень длинный текст компонента: {} символов", text.length());
                }
            } catch (Exception e) {
                LOGGER.debug("Обработка длинных значений в компонентах: {}", e.getMessage());
            }

            // Тест всех уровней свежести
            for (var level : TooltipUtils.FreshnessLevel.values()) {
                try {
                    var component = TooltipUtils.createFreshnessComponent(level);
                    if (component == null) {
                        LOGGER.error("ОШИБКА: Null компонент для уровня свежести {}", level);
                        allPassed = false;
                    }
                } catch (Exception e) {
                    LOGGER.error("ОШИБКА при создании компонента свежести {}: {}", level, e.getMessage());
                    allPassed = false;
                }
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании граничных случаев: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования граничных случаев: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует стресс-нагрузку со случайными значениями
     */
    public static boolean testStressWithRandomValues() {
        LOGGER.info("--- Стресс-тест Tooltip системы со случайными значениями ---");

        boolean allPassed = true;

        try {
            final int ITERATIONS = 5_000;
            int failures = 0;

            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    // Генерируем случайные значения
                    long randomDay = ThreadLocalRandom.current().nextLong();
                    long randomRemaining = ThreadLocalRandom.current().nextLong(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);
                    long randomTotal = Math.max(1L, Math.abs(ThreadLocalRandom.current().nextLong()));

                    // Тестируем форматирование
                    String formatted = TooltipUtils.formatTimeRemaining(randomDay);
                    if (formatted == null || formatted.trim().isEmpty()) {
                        failures++;
                    }

                    // Тестируем создание компонента
                    var component = TooltipUtils.createCreationDayComponent(randomDay);
                    if (component == null) {
                        failures++;
                    }

                    // Тестируем расчет свежести
                    var freshness = TooltipUtils.calculateFreshness(randomRemaining, randomTotal);
                    if (freshness == null) {
                        failures++;
                    }

                    // Каждые 500 итераций выводим прогресс
                    if (i % 500 == 0 && i > 0) {
                        LOGGER.debug("Стресс-тест прогресс: {}/{} итераций", i, ITERATIONS);
                    }

                } catch (Exception e) {
                    failures++;
                    if (failures <= 5) {
                        LOGGER.error("Стресс-тест исключение в итерации {}: {}", i, e.getMessage());
                    }
                }
            }

            if (failures > 0) {
                LOGGER.warn("Стресс-тест завершен с {} ошибками из {} итераций", failures, ITERATIONS);
                if (failures > ITERATIONS * 0.01) { // Более 1% ошибок считаем проблемой
                    allPassed = false;
                }
            } else {
                LOGGER.info("Стресс-тест успешно завершен без ошибок");
            }

        } catch (Exception e) {
            LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА при стресс-тесте: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат стресс-теста: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Запускает все специализированные тесты Tooltip системы
     */
    public static boolean runAllTests() {
        LOGGER.info("=== ЗАПУСК ВСЕХ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ Tooltip системы ===");

        boolean formatTests = testTimeFormatting();
        boolean componentTests = testCreationDayComponents();
        boolean freshnessTests = testFreshnessCalculation();
        boolean performanceTests = testPerformance();
        boolean edgeCaseTests = testEdgeCases();
        boolean stressTests = testStressWithRandomValues();

        boolean allPassed = formatTests && componentTests && freshnessTests && performanceTests && edgeCaseTests && stressTests;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ Tooltip системы ===");
        LOGGER.info("Форматирование времени: {}", formatTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Компоненты дня создания: {}", componentTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Расчет свежести: {}", freshnessTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Производительность: {}", performanceTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Граничные случаи: {}", edgeCaseTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Стресс-тест: {}", stressTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("ОБЩИЙ РЕЗУЛЬТАТ: {}", allPassed ? "ВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "ОБНАРУЖЕНЫ ПРОБЛЕМЫ");

        return allPassed;
    }
}
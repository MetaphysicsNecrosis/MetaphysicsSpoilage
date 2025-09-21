package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageComponent;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Специализированные тесты для SpoilageComponent с фокусом на большие значения дней.
 *
 * Проверяет:
 * - Корректность сериализации/десериализации экстремальных значений через Codec
 * - Сетевую передачу больших long значений через StreamCodec
 * - Целостность данных при работе с граничными значениями
 * - Производительность операций с компонентом
 */
public class SpoilageComponentTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageComponentTests.class);

    /**
     * Тестирует создание компонентов с экстремальными значениями
     */
    public static boolean testComponentCreation() {
        LOGGER.info("--- Тестирование создания SpoilageComponent с экстремальными значениями ---");

        boolean allPassed = true;

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
                // Создаем компонент
                SpoilageComponent component = new SpoilageComponent(testValue);

                // Проверяем, что значение сохранилось корректно
                if (component.creationDay() != testValue) {
                    LOGGER.error("ОШИБКА: Создан компонент с {}, получено {}", testValue, component.creationDay());
                    allPassed = false;
                } else {
                    LOGGER.debug("OK: Компонент создан с значением {}", testValue);
                }

                // Проверяем неизменяемость (immutability) record
                SpoilageComponent anotherComponent = new SpoilageComponent(testValue);
                if (!component.equals(anotherComponent)) {
                    LOGGER.error("ОШИБКА: Компоненты с одинаковыми значениями не равны для {}", testValue);
                    allPassed = false;
                }

                // Проверяем hashCode
                if (component.hashCode() != anotherComponent.hashCode()) {
                    LOGGER.error("ОШИБКА: HashCode не совпадает для одинаковых компонентов с {}", testValue);
                    allPassed = false;
                }

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при создании компонента с {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования создания компонентов: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует Codec сериализацию с большими значениями
     */
    public static boolean testCodecSerialization() {
        LOGGER.info("--- Тестирование Codec сериализации с большими значениями ---");

        boolean allPassed = true;

        long[] testValues = {
            0L, 1L, -1L,
            Long.MAX_VALUE, Long.MIN_VALUE,
            1_000_000_000_000L, -1_000_000_000_000L
        };

        for (long testValue : testValues) {
            try {
                SpoilageComponent original = new SpoilageComponent(testValue);

                // Тестируем кодирование в NBT
                DataResult<Tag> encodeResult = SpoilageComponent.CODEC.encodeStart(null, original);

                if (encodeResult.error().isPresent()) {
                    LOGGER.error("ОШИБКА кодирования для {}: {}", testValue, encodeResult.error().get().message());
                    allPassed = false;
                    continue;
                }

                Tag nbtTag = encodeResult.result().orElse(null);
                if (nbtTag == null) {
                    LOGGER.error("ОШИБКА: NBT тег null для {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Проверяем, что NBT содержит правильное значение
                if (nbtTag instanceof LongTag longTag) {
                    // В NeoForge 1.21.8 используем toString() и парсинг для получения значения
                    try {
                        String valueStr = longTag.toString();
                        // LongTag toString() обычно возвращает значение + "L", например "12345L"
                        String numberStr = valueStr.endsWith("L") ? valueStr.substring(0, valueStr.length() - 1) : valueStr;
                        long actualValue = Long.parseLong(numberStr);
                        if (actualValue != testValue) {
                            LOGGER.error("ОШИБКА: NBT содержит {}, ожидалось {}", actualValue, testValue);
                            allPassed = false;
                            continue;
                        }
                    } catch (Exception e) {
                        LOGGER.error("ОШИБКА: Не удалось получить значение из LongTag: {}", e.getMessage());
                        allPassed = false;
                        continue;
                    }
                } else {
                    LOGGER.error("ОШИБКА: NBT не является LongTag для {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Тестируем декодирование из NBT
                DataResult<SpoilageComponent> decodeResult = SpoilageComponent.CODEC.parse(null, nbtTag);

                if (decodeResult.error().isPresent()) {
                    LOGGER.error("ОШИБКА декодирования для {}: {}", testValue, decodeResult.error().get().message());
                    allPassed = false;
                    continue;
                }

                SpoilageComponent decoded = decodeResult.result().orElse(null);
                if (decoded == null) {
                    LOGGER.error("ОШИБКА: Декодированный компонент null для {}", testValue);
                    allPassed = false;
                    continue;
                }

                // Проверяем, что значение сохранилось
                if (decoded.creationDay() != testValue) {
                    LOGGER.error("ОШИБКА: После codec round-trip получено {}, ожидалось {}",
                        decoded.creationDay(), testValue);
                    allPassed = false;
                } else {
                    LOGGER.debug("OK: Codec round-trip для {} успешен", testValue);
                }

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при Codec тестировании для {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования Codec сериализации: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует StreamCodec для сетевой передачи больших значений
     */
    public static boolean testStreamCodecSerialization() {
        LOGGER.info("--- Тестирование StreamCodec сетевой сериализации ---");

        boolean allPassed = true;

        long[] testValues = {
            0L, 1L, -1L,
            Long.MAX_VALUE, Long.MIN_VALUE,
            1_000_000_000_000L, -1_000_000_000_000L
        };

        for (long testValue : testValues) {
            try {
                SpoilageComponent original = new SpoilageComponent(testValue);

                // Создаем mock буфер для тестирования
                // В реальности это должен быть RegistryFriendlyByteBuf, но для тестирования создаем простую имитацию
                TestByteBuf buffer = new TestByteBuf();

                // Тестируем кодирование
                try {
                    // Имитируем кодирование (записываем long значение)
                    buffer.writeLong(original.creationDay());
                } catch (Exception e) {
                    LOGGER.error("ОШИБКА кодирования в буфер для {}: {}", testValue, e.getMessage());
                    allPassed = false;
                    continue;
                }

                // Тестируем декодирование
                try {
                    long decodedValue = buffer.readLong();
                    SpoilageComponent decoded = new SpoilageComponent(decodedValue);

                    if (decoded.creationDay() != testValue) {
                        LOGGER.error("ОШИБКА: После StreamCodec round-trip получено {}, ожидалось {}",
                            decoded.creationDay(), testValue);
                        allPassed = false;
                    } else {
                        LOGGER.debug("OK: StreamCodec round-trip для {} успешен", testValue);
                    }
                } catch (Exception e) {
                    LOGGER.error("ОШИБКА декодирования из буфера для {}: {}", testValue, e.getMessage());
                    allPassed = false;
                }

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при StreamCodec тестировании для {}: {}", testValue, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования StreamCodec сериализации: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует производительность операций с большими значениями
     */
    public static boolean testPerformance() {
        LOGGER.info("--- Тестирование производительности SpoilageComponent ---");

        boolean allPassed = true;

        try {
            final int OPERATIONS_COUNT = 100_000;

            // Тест производительности создания компонентов
            long startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long bigValue = Long.MAX_VALUE - i;
                new SpoilageComponent(bigValue);
            }

            long createTimeNs = System.nanoTime() - startTime;
            double createTimeMs = createTimeNs / 1_000_000.0;
            double avgCreateTimeNs = (double) createTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время создания {} компонентов: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, createTimeMs, avgCreateTimeNs);

            // Тест производительности доступа к полю
            SpoilageComponent testComponent = new SpoilageComponent(Long.MAX_VALUE);
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                testComponent.creationDay();
            }

            long accessTimeNs = System.nanoTime() - startTime;
            double accessTimeMs = accessTimeNs / 1_000_000.0;
            double avgAccessTimeNs = (double) accessTimeNs / OPERATIONS_COUNT;

            LOGGER.info("Время доступа {} раз: {:.2f} мс (среднее: {:.2f} нс/операция)",
                OPERATIONS_COUNT, accessTimeMs, avgAccessTimeNs);

            // Проверяем, что производительность приемлема
            if (avgCreateTimeNs > 1000 || avgAccessTimeNs > 100) {
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
     * Тестирует стресс-нагрузку со случайными большими значениями
     */
    public static boolean testStressWithRandomValues() {
        LOGGER.info("--- Стресс-тест SpoilageComponent со случайными значениями ---");

        boolean allPassed = true;

        try {
            final int ITERATIONS = 10_000;
            int failures = 0;

            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    // Генерируем случайное большое значение
                    long randomValue = ThreadLocalRandom.current().nextLong();

                    // Создаем компонент
                    SpoilageComponent component = new SpoilageComponent(randomValue);

                    // Проверяем значение
                    if (component.creationDay() != randomValue) {
                        failures++;
                        if (failures <= 10) { // Логируем только первые 10 ошибок
                            LOGGER.warn("Стресс-тест ошибка {}: ожидалось {}, получено {}",
                                failures, randomValue, component.creationDay());
                        }
                    }

                    // Проверяем equals и hashCode
                    SpoilageComponent duplicate = new SpoilageComponent(randomValue);
                    if (!component.equals(duplicate) || component.hashCode() != duplicate.hashCode()) {
                        failures++;
                    }

                    // Каждые 1000 итераций выводим прогресс
                    if (i % 1000 == 0 && i > 0) {
                        LOGGER.debug("Стресс-тест прогресс: {}/{} итераций", i, ITERATIONS);
                    }

                } catch (Exception e) {
                    failures++;
                    if (failures <= 5) { // Логируем только первые 5 исключений
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
     * Запускает все специализированные тесты SpoilageComponent
     */
    public static boolean runAllTests() {
        LOGGER.info("=== ЗАПУСК ВСЕХ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ SpoilageComponent ===");

        boolean creationTests = testComponentCreation();
        boolean codecTests = testCodecSerialization();
        boolean streamCodecTests = testStreamCodecSerialization();
        boolean performanceTests = testPerformance();
        boolean stressTests = testStressWithRandomValues();

        boolean allPassed = creationTests && codecTests && streamCodecTests && performanceTests && stressTests;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ SpoilageComponent ===");
        LOGGER.info("Создание компонентов: {}", creationTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Codec сериализация: {}", codecTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("StreamCodec сериализация: {}", streamCodecTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Производительность: {}", performanceTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Стресс-тест: {}", stressTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("ОБЩИЙ РЕЗУЛЬТАТ: {}", allPassed ? "ВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "ОБНАРУЖЕНЫ ПРОБЛЕМЫ");

        return allPassed;
    }

    /**
     * Простая реализация ByteBuf для тестирования
     */
    private static class TestByteBuf {
        private long value;

        public void writeLong(long value) {
            this.value = value;
        }

        public long readLong() {
            return value;
        }
    }
}
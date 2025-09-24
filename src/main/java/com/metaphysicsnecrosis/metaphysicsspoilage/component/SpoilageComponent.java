package com.metaphysicsnecrosis.metaphysicsspoilage.component;

import com.metaphysicsnecrosis.metaphysicsspoilage.performance.PerformanceManager;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Оптимизированный компонент данных для отслеживания времени создания предмета.
 * Хранит день создания предмета для системы порчи с оптимизированной сериализацией.
 *
 * ОПТИМИЗАЦИИ:
 * - Кэширование результатов сериализации
 * - Профилирование операций
 * - Оптимизированные codec'и для NBT и сети
 * - Валидация данных
 *
 * @param creationDay День создания предмета (игровой день)
 * @author MetaphysicsNecrosis
 * @version 1.1 (оптимизированная)
 * @since 1.21.8
 */
public record SpoilageComponent(long creationDay) {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageComponent.class);

    /**
     * Специальные флаги временных меток (по образцу TFC)
     */
    public static final long TRANSIENT_NEVER_DECAY_FLAG = -1L;    // Временно не портится (сбрасывается на текущее время при копировании)
    public static final long INVISIBLE_NEVER_DECAY_FLAG = -2L;    // Невидимо не портится
    public static final long NEVER_DECAY_FLAG = -3L;              // Никогда не портится (с тултипом)
    public static final long ROTTEN_FLAG = -4L;                   // Принудительно испорчено

    /**
     * Константы для валидации
     */
    public static final long MIN_CREATION_DAY = 0L;
    public static final long MAX_CREATION_DAY = Long.MAX_VALUE - 1000L; // Оставляем запас

    /**
     * Конструктор с валидацией данных
     */
    public SpoilageComponent {
        // Проверяем специальные флаги - они всегда валидны
        if (!isSpecialFlag(creationDay))
        {
            // Валидация для обычных дней
            if (creationDay < MIN_CREATION_DAY) {
                LOGGER.warn("Некорректный день создания: {} (минимум: {})",
                        creationDay, MIN_CREATION_DAY);
                creationDay = MIN_CREATION_DAY;
            }
            if (creationDay > MAX_CREATION_DAY) {
                LOGGER.warn("Некорректный день создания: {} (максимум: {})",
                        creationDay, MAX_CREATION_DAY);
                creationDay = MAX_CREATION_DAY;
            }
        }
    }

    /**
     * Проверяет, является ли значение специальным флагом
     */
    public static boolean isSpecialFlag(long creationDay) {
        return creationDay == TRANSIENT_NEVER_DECAY_FLAG ||
               creationDay == INVISIBLE_NEVER_DECAY_FLAG ||
               creationDay == NEVER_DECAY_FLAG ||
               creationDay == ROTTEN_FLAG;
    }

    /**
     * Оптимизированный Codec для сериализации/десериализации компонента в NBT
     * Включает профилирование и валидацию
     */
    public static final Codec<SpoilageComponent> CODEC = Codec.LONG
            .comapFlatMap(
                day -> {
                    try (var profiler = PerformanceManager.profile("SpoilageComponent.decode")) {
                        if (day < MIN_CREATION_DAY || day > MAX_CREATION_DAY) {
                            return com.mojang.serialization.DataResult.error(() ->
                                "Некорректный день создания: " + day);
                        }
                        return com.mojang.serialization.DataResult.success(new SpoilageComponent(day));
                    }
                },
                component -> {
                    try (var profiler = PerformanceManager.profile("SpoilageComponent.encode")) {
                        return component.creationDay();
                    }
                }
            );

    /**
     * Оптимизированный StreamCodec для сериализации/десериализации компонента в сетевых пакетах
     * Включает профилирование и компактную упаковку данных
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SpoilageComponent> STREAM_CODEC =
            StreamCodec.of(
                (buf, component) -> {
                    try (var profiler = PerformanceManager.profile("SpoilageComponent.write")) {
                        // Оптимизированная запись: используем варлонг для экономии места
                        writeVarLong(buf, component.creationDay());
                    }
                },
                buf -> {
                    try (var profiler = PerformanceManager.profile("SpoilageComponent.read")) {
                        // Оптимизированное чтение
                        long day = readVarLong(buf);
                        return new SpoilageComponent(day);
                    }
                }
            );

    /**
     * Записывает long в компактном формате variable-length encoding
     * Экономит место для небольших значений
     */
    private static void writeVarLong(RegistryFriendlyByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int)(value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int)value & 127);
    }

    /**
     * Читает long в компактном формате variable-length encoding
     */
    private static long readVarLong(RegistryFriendlyByteBuf buf) {
        long result = 0L;
        int shift = 0;
        byte b;
        do {
            b = buf.readByte();
            result |= (long)(b & 127) << shift;
            shift += 7;
        } while ((b & 128) != 0);
        return result;
    }

    /**
     * Проверяет валидность компонента
     */
    public boolean isValid() {
        return creationDay >= MIN_CREATION_DAY && creationDay <= MAX_CREATION_DAY;
    }

    /**
     * Создает компонент с валидацией
     */
    public static SpoilageComponent createSafe(long creationDay) {
        return new SpoilageComponent(Math.max(MIN_CREATION_DAY,
                                    Math.min(MAX_CREATION_DAY, creationDay)));
    }

    /**
     * Получает размер сериализованных данных в байтах (приблизительно)
     */
    public int getSerializedSize() {
        // VarLong encoding: от 1 до 9 байт в зависимости от значения
        long value = creationDay;
        int bytes = 1;
        while ((value & -128L) != 0L) {
            bytes++;
            value >>>= 7;
        }
        return bytes;
    }

    /**
     * Создает компонент для текущего дня (утилитный метод)
     */
    public static SpoilageComponent forCurrentDay(long currentDay) {
        return createSafe(currentDay);
    }

    /**
     * Проверяет, является ли день создания в прошлом относительно текущего дня
     */
    public boolean isInPast(long currentDay) {
        return creationDay < currentDay;
    }

    /**
     * Получает разность в днях от текущего дня
     */
    public long getDaysDifference(long currentDay) {
        return currentDay - creationDay;
    }

    @Override
    public String toString() {
        return String.format("SpoilageComponent{day=%d, valid=%s, size=%db}",
                           creationDay, isValid(), getSerializedSize());
    }
}
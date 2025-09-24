package com.metaphysicsnecrosis.metaphysicsspoilage.component;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Упрощенная система управления временными метками для еды.
 * Заменяет тяжелую систему FoodReplacementHandler простой логикой на уровне ItemStack.
 *
 * Основана на подходе TerraFirmaCraft - автоматическое присваивание компонентов
 * при создании ItemStack через Mixin.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public final class SpoilageHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageHooks.class);

    /**
     * Кэш для проверки возможности порчи предметов (для производительности)
     */
    private static final Map<String, Boolean> CAN_SPOIL_CACHE = new ConcurrentHashMap<>(128);

    /**
     * Кэш для исключенных предметов
     */
    private static final Map<String, Boolean> EXCLUDED_CACHE = new ConcurrentHashMap<>(64);

    /**
     * Флаг включения системы (для быстрой проверки)
     */
    private static volatile boolean systemEnabled = true;

    /**
     * Вызывается при создании каждого ItemStack через Mixin.
     * Автоматически добавляет временную метку если предмет является едой.
     *
     * @param stack Созданный ItemStack
     */
    public static void onItemStackCreated(ItemStack stack) {
        // Быстрые проверки для производительности
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // КРИТИЧЕСКИ ВАЖНО: Проверяем, загружена ли конфигурация
        // Mixin вызывается очень рано, когда конфиг еще может быть не готов
        if (!isConfigLoaded()) {
            return;
        }

        // Проверяем, включена ли система
        if (!isSystemEnabled()) {
            return;
        }

        // Проверяем, уже есть ли временная метка
        if (SpoilageUtils.hasTimestamp(stack)) {
            return;
        }

        // Проверяем, может ли предмет портиться
        if (!canItemSpoilCached(stack)) {
            return;
        }

        // Добавляем временную метку
        addTimestampToStack(stack);
    }

    /**
     * Проверяет, может ли предмет портиться (с кэшированием)
     */
    private static boolean canItemSpoilCached(ItemStack stack) {
        try {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            return CAN_SPOIL_CACHE.computeIfAbsent(itemId, id -> {
                try {
                    // Проверяем исключения
                    if (isItemExcludedCached(id)) {
                        return false;
                    }

                    // Проверяем базовую способность к порче
                    return SpoilageUtils.canItemSpoil(stack.getItem());
                } catch (Exception e) {
                    // При любой ошибке считаем, что предмет не может портиться
                    LOGGER.debug("Ошибка при проверке способности к порче для {}: {}", id, e.getMessage());
                    return false;
                }
            });
        } catch (Exception e) {
            // Если даже получить ID предмета не удается
            LOGGER.debug("Ошибка при получении ID предмета: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет, исключен ли предмет (с кэшированием)
     */
    private static boolean isItemExcludedCached(String itemId) {
        return EXCLUDED_CACHE.computeIfAbsent(itemId, id -> {
            try {
                return Config.isItemExcluded(id);
            } catch (Exception e) {
                // При ошибке считаем предмет НЕ исключенным (безопасная сторона)
                LOGGER.debug("Ошибка при проверке исключения для {}: {}", id, e.getMessage());
                return false;
            }
        });
    }

    /**
     * Добавляет временную метку к предмету
     */
    private static void addTimestampToStack(ItemStack stack) {
        try {
            // Получаем текущий день
            long currentDay = getCurrentDay();

            // Устанавливаем компонент
            SpoilageComponent component = new SpoilageComponent(currentDay);
            stack.set(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get(), component);

            LOGGER.debug("Добавлена временная метка к {} (день: {})",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), currentDay);

        } catch (Exception e) {
            LOGGER.error("Ошибка при добавлении временной метки к предмету {}: {}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), e.getMessage());
        }
    }

    /**
     * Устанавливает специальный флаг для предмета (по образцу TFC)
     */
    public static ItemStack setSpecialFlag(ItemStack stack, long flag) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }

        try {
            SpoilageComponent component = new SpoilageComponent(flag);
            stack.set(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get(), component);
            LOGGER.debug("Установлен специальный флаг {} для {}",
                flag, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } catch (Exception e) {
            LOGGER.error("Ошибка при установке специального флага для {}: {}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), e.getMessage());
        }

        return stack;
    }

    /**
     * Устанавливает флаг "временно не портится" (для креативного инвентаря, рецептов)
     */
    public static ItemStack setTransientNonDecaying(ItemStack stack) {
        return setSpecialFlag(stack, SpoilageComponent.TRANSIENT_NEVER_DECAY_FLAG);
    }

    /**
     * Устанавливает флаг "никогда не портится"
     */
    public static ItemStack setNeverDecaying(ItemStack stack) {
        return setSpecialFlag(stack, SpoilageComponent.NEVER_DECAY_FLAG);
    }

    /**
     * Устанавливает текущее время как время создания (для "свежей" еды)
     */
    public static ItemStack setFreshTimestamp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }

        try {
            long currentDay = getCurrentDay();
            SpoilageComponent component = new SpoilageComponent(currentDay);
            stack.set(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get(), component);
            LOGGER.debug("Установлена свежая временная метка для {} (день: {})",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), currentDay);
        } catch (Exception e) {
            LOGGER.error("Ошибка при установке свежей временной метки для {}: {}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), e.getMessage());
        }

        return stack;
    }

    /**
     * Получает текущий игровой день
     */
    private static long getCurrentDay() {
        try {
            // Пытаемся получить серверный уровень
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    return WorldDayTracker.getInstance(overworld).getCurrentDay();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Не удалось получить серверный уровень, используем день 0: {}", e.getMessage());
        }

        // Fallback - возвращаем день 0 если нет доступа к серверу
        // Это может произойти на клиенте или при загрузке
        return 0L;
    }

    /**
     * Проверяет, загружена ли конфигурация и готова ли система
     */
    private static boolean isConfigLoaded() {
        try {
            // Пытаемся получить значение конфига без исключений
            Config.ENABLE_SPOILAGE_SYSTEM.get();
            return true;
        } catch (IllegalStateException e) {
            // Конфигурация еще не загружена
            return false;
        } catch (Exception e) {
            // Любая другая ошибка - считаем, что система не готова
            LOGGER.debug("Ошибка при проверке готовности конфига: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет, включена ли система порчи
     */
    private static boolean isSystemEnabled() {
        try {
            // Кэшируем результат для производительности
            boolean currentValue = Config.ENABLE_SPOILAGE_SYSTEM.get();
            if (systemEnabled != currentValue) {
                systemEnabled = currentValue;
            }
            return systemEnabled;
        } catch (Exception e) {
            // Если не удается получить значение, считаем систему отключенной
            LOGGER.debug("Не удается получить настройку ENABLE_SPOILAGE_SYSTEM: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Принудительно обновляет временную метку для существующего предмета
     * (для совместимости со старой системой)
     */
    public static void forceUpdateTimestamp(ItemStack stack, ServerLevel level) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (!isSystemEnabled()) {
            return;
        }

        if (!SpoilageUtils.canItemSpoil(stack.getItem())) {
            return;
        }

        // Устанавливаем новую временную метку
        long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();
        SpoilageComponent component = new SpoilageComponent(currentDay);
        stack.set(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get(), component);

        LOGGER.debug("Принудительно обновлена временная метка для {} (день: {})",
            BuiltInRegistries.ITEM.getKey(stack.getItem()), currentDay);
    }

    /**
     * Очищает кэши (для тестирования и перезагрузки конфигурации)
     */
    public static void clearCaches() {
        CAN_SPOIL_CACHE.clear();
        EXCLUDED_CACHE.clear();
        systemEnabled = Config.ENABLE_SPOILAGE_SYSTEM.get();
        LOGGER.debug("Очищены кэши SpoilageHooks");
    }

    /**
     * Получает статистику кэшей
     */
    public static String getCacheStats() {
        return String.format("SpoilageHooks кэши - CanSpoil: %d, Excluded: %d, System: %s",
            CAN_SPOIL_CACHE.size(), EXCLUDED_CACHE.size(), systemEnabled);
    }

    /**
     * Валидирует корректность работы системы
     */
    public static boolean validate() {
        try {
            // Проверяем доступность компонента
            if (MetaphysicsSpoilage.SPOILAGE_COMPONENT.get() == null) {
                LOGGER.error("SpoilageComponent не зарегистрирован");
                return false;
            }

            // Проверяем базовые функции
            boolean systemCheck = isSystemEnabled();
            LOGGER.info("Валидация SpoilageHooks прошла успешно. Система включена: {}", systemCheck);
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации SpoilageHooks", e);
            return false;
        }
    }

    // Запрещаем создание экземпляров
    private SpoilageHooks() {}
}
package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageComponent;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Утилитный класс для работы с компонентами порчи предметов.
 * Предоставляет методы для установки, получения и проверки временных меток на ItemStack.
 */
public class SpoilageUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageUtils.class);

    /**
     * УДАЛЕНО: Hardcoded настройки перенесены в JSON файлы
     * Теперь все настройки загружаются из datapack JSON файлов
     */

    /**
     * Проверяет, есть ли у предмета временная метка компонента порчи
     *
     * @param itemStack Стек предметов для проверки
     * @return true, если у предмета есть компонент порчи
     */
    public static boolean hasTimestamp(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        return itemStack.has(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get());
    }

    /**
     * Получает день создания предмета из компонента порчи
     *
     * @param itemStack Стек предметов
     * @return День создания предмета или -1, если компонент отсутствует
     */
    public static long getCreationDay(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return -1;
        }

        SpoilageComponent component = itemStack.get(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get());
        if (component != null) {
            return component.creationDay();
        }

        return -1;
    }

    /**
     * Устанавливает день создания предмета в компоненте порчи
     *
     * @param itemStack Стек предметов
     * @param creationDay День создания предмета
     */
    public static void setCreationDay(ItemStack itemStack, long creationDay) {
        if (itemStack.isEmpty()) {
            return;
        }

        SpoilageComponent component = new SpoilageComponent(creationDay);
        itemStack.set(MetaphysicsSpoilage.SPOILAGE_COMPONENT.get(), component);
    }

    /**
     * Проверяет, испортился ли предмет на основе текущего игрового дня
     *
     * @param itemStack Стек предметов для проверки
     * @param level Серверный уровень для получения текущего дня
     * @return true, если предмет испортился
     */
    public static boolean isItemSpoiled(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return false;
        }

        // Получаем данные о порче для этого предмета
        SpoilageData spoilageData = getSpoilageData(itemStack.getItem());
        if (!spoilageData.canSpoil()) {
            return false;
        }

        // Проверяем наличие компонента порчи
        long creationDay = getCreationDay(itemStack);
        if (creationDay == -1) {
            return false;
        }

        // Получаем текущий день
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long currentDay = tracker.getCurrentDay();

        // Проверяем, прошло ли достаточно времени для порчи
        long daysSinceCreation = currentDay - creationDay;
        return daysSinceCreation >= spoilageData.getSpoilageTime();
    }

    /**
     * Получает данные о порче для указанного предмета
     *
     * @param item Предмет
     * @return Данные о порче предмета
     */
    public static SpoilageData getSpoilageData(Item item) {
        // 0. ПРОВЕРКА ИСКЛЮЧЕНИЙ (всегда первая проверка)
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        // Проверяем исключения из конфига
        if (Config.isItemExcluded(itemId)) {
            LOGGER.debug("Предмет {} исключен из системы порчи", itemId);
            return SpoilageData.nonSpoilable();
        }

        // Проверяем автоматические исключения
        if (isAutomaticallyExcluded(item, itemId)) {
            LOGGER.debug("Предмет {} автоматически исключен из системы порчи", itemId);
            return SpoilageData.nonSpoilable();
        }

        // 1. Проверяем JSON конфигурацию (единственный источник настроек)
        SpoilageData jsonData = JsonSpoilageConfig.getJsonSpoilageData(item);
        if (jsonData != null) {
            return jsonData;
        }

        // 2. УНИВЕРСАЛЬНАЯ ПОДДЕРЖКА: если включена универсальная порча еды
        if (Config.ENABLE_UNIVERSAL_FOOD_SPOILAGE.get() && hasUniversalFoodComponent(item)) {
            // Получаем дефолтное время из конфига (2 дня)
            long defaultDays = Config.DEFAULT_FOOD_STORAGE_DAYS.get();
            return SpoilageData.spoilable(defaultDays);
        }

        return SpoilageData.nonSpoilable();
    }

    /**
     * УДАЛЕНО: registerSpoilageData больше не нужен
     * Все настройки теперь управляются через JSON файлы в datapack'ах
     */

    /**
     * Проверяет, может ли предмет портиться
     *
     * @param item Предмет для проверки
     * @return true, если предмет может портиться
     */
    public static boolean canItemSpoil(Item item) {
        return getSpoilageData(item).canSpoil();
    }

    /**
     * Получает количество дней до полной порчи предмета
     *
     * @param item Предмет
     * @return Количество дней до порчи, или -1 если предмет не портится
     */
    public static long getSpoilageTime(Item item) {
        SpoilageData data = getSpoilageData(item);
        return data.canSpoil() ? data.getSpoilageTime() : -1;
    }

    /**
     * Автоматически устанавливает временную метку для предмета, если он может портиться
     * и у него еще нет метки
     *
     * @param itemStack Стек предметов
     * @param level Серверный уровень для получения текущего дня
     */
    public static void autoSetTimestamp(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return;
        }

        // Проверяем, может ли предмет портиться
        if (!canItemSpoil(itemStack.getItem())) {
            return;
        }

        // Устанавливаем метку только если её еще нет
        if (!hasTimestamp(itemStack)) {
            WorldDayTracker tracker = WorldDayTracker.getInstance(level);
            setCreationDay(itemStack, tracker.getCurrentDay());
        }
    }

    /**
     * Получает количество дней до порчи предмета
     *
     * @param itemStack Стек предметов
     * @param level Серверный уровень
     * @return Количество дней до порчи, или -1 если предмет не портится или уже испорчен
     */
    public static long getDaysUntilSpoilage(ItemStack itemStack, ServerLevel level) {
        if (itemStack.isEmpty()) {
            return -1;
        }

        SpoilageData spoilageData = getSpoilageData(itemStack.getItem());
        if (!spoilageData.canSpoil()) {
            return -1;
        }

        long creationDay = getCreationDay(itemStack);
        if (creationDay == -1) {
            return -1;
        }

        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long currentDay = tracker.getCurrentDay();
        long daysSinceCreation = currentDay - creationDay;
        long daysUntilSpoilage = spoilageData.getSpoilageTime() - daysSinceCreation;

        return Math.max(0, daysUntilSpoilage);
    }

    /**
     * Универсальная проверка: является ли предмет едой (имеет FOOD компонент)
     *
     * @param item предмет для проверки
     * @return true, если предмет является едой
     */
    public static boolean hasUniversalFoodComponent(Item item) {
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
     * Проверяет, должен ли предмет быть автоматически исключен из системы порчи
     * на основе настроек в Config
     *
     * @param item предмет для проверки
     * @param itemId строковый ID предмета
     * @return true, если предмет должен быть исключен
     */
    private static boolean isAutomaticallyExcluded(Item item, String itemId) {
        // Исключаем уже испорченные предметы
        if (Config.EXCLUDE_ALREADY_SPOILED_ITEMS.get()) {
            if (itemId.equals("minecraft:rotten_flesh") ||
                itemId.equals("minecraft:spider_eye") ||
                itemId.equals("minecraft:poisonous_potato")) {
                return true;
            }
        }

        // Исключаем магическую еду
        if (Config.EXCLUDE_MAGICAL_FOOD.get()) {
            if (itemId.equals("minecraft:golden_apple") ||
                itemId.equals("minecraft:enchanted_golden_apple") ||
                itemId.equals("minecraft:chorus_fruit")) {
                return true;
            }
        }

        // Исключаем зелья
        if (Config.EXCLUDE_POTIONS_FROM_SPOILAGE.get()) {
            if (itemId.equals("minecraft:potion") ||
                itemId.equals("minecraft:splash_potion") ||
                itemId.equals("minecraft:lingering_potion") ||
                itemId.equals("minecraft:honey_bottle")) { // Мёд как "зелье" долговременного хранения
                return true;
            }
        }

        return false;
    }
}
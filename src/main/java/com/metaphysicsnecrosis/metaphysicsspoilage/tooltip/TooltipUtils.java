package com.metaphysicsnecrosis.metaphysicsspoilage.tooltip;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Утилитный класс для создания и форматирования тултипов системы порчи еды.
 * Обеспечивает цветовое кодирование свежести продуктов и форматирование информации.
 *
 * ПРИМЕЧАНИЕ: Используется только на клиентской стороне.
 */
public class TooltipUtils {

    /**
     * Enum для определения свежести продукта на основе оставшегося времени
     */
    public enum FreshnessLevel {
        FRESH(ChatFormatting.GREEN, "tooltip.metaphysicsspoilage.freshness.fresh"),
        SLIGHTLY_STALE(ChatFormatting.YELLOW, "tooltip.metaphysicsspoilage.freshness.slightly_stale"),
        STALE(ChatFormatting.GOLD, "tooltip.metaphysicsspoilage.freshness.stale"),
        SPOILED(ChatFormatting.RED, "tooltip.metaphysicsspoilage.freshness.spoiled");

        private final ChatFormatting color;
        private final String translationKey;

        FreshnessLevel(ChatFormatting color, String translationKey) {
            this.color = color;
            this.translationKey = translationKey;
        }

        public ChatFormatting getColor() {
            return color;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    /**
     * Определяет уровень свежести продукта на основе процента оставшегося времени
     *
     * @param daysRemaining Дней до порчи
     * @param totalSpoilageDays Общее время до порчи
     * @return Уровень свежести
     */
    public static FreshnessLevel calculateFreshness(long daysRemaining, long totalSpoilageDays) {
        if (daysRemaining <= 0) {
            return FreshnessLevel.SPOILED;
        }

        double freshnessPercent = (double) daysRemaining / totalSpoilageDays;

        if (freshnessPercent > 0.5) {
            return FreshnessLevel.FRESH; // Более 50% времени
        } else if (freshnessPercent > 0.25) {
            return FreshnessLevel.SLIGHTLY_STALE; // 25-50% времени
        } else if (freshnessPercent > 0.1) {
            return FreshnessLevel.STALE; // 10-25% времени
        } else {
            return FreshnessLevel.SPOILED; // Менее 10% времени или просрочено
        }
    }

    /**
     * Создает компонент для отображения дня создания
     *
     * @param creationDay День создания
     * @return Форматированный компонент
     */
    public static Component createCreationDayComponent(long creationDay) {
        return Component.translatable("tooltip.metaphysicsspoilage.creation_day", creationDay)
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * Создает компонент для отображения дня порчи
     *
     * @param spoilageDay День порчи
     * @return Форматированный компонент
     */
    public static Component createSpoilageDayComponent(long spoilageDay) {
        return Component.translatable("tooltip.metaphysicsspoilage.spoilage_day", spoilageDay)
                .withStyle(ChatFormatting.GRAY);
    }

    /**
     * Создает компонент для отображения оставшихся дней
     *
     * @param daysRemaining Дней до порчи
     * @param freshness Уровень свежести
     * @return Форматированный компонент
     */
    public static Component createDaysRemainingComponent(long daysRemaining, FreshnessLevel freshness) {
        if (daysRemaining <= 0) {
            return Component.translatable("tooltip.metaphysicsspoilage.days_remaining.spoiled")
                    .withStyle(freshness.getColor());
        } else {
            return Component.translatable("tooltip.metaphysicsspoilage.days_remaining", daysRemaining)
                    .withStyle(freshness.getColor());
        }
    }

    /**
     * Создает компонент для отображения состояния свежести
     *
     * @param freshness Уровень свежести
     * @return Форматированный компонент
     */
    public static Component createFreshnessComponent(FreshnessLevel freshness) {
        return Component.translatable("tooltip.metaphysicsspoilage.freshness_label")
                .append(": ")
                .append(Component.translatable(freshness.getTranslationKey()))
                .withStyle(freshness.getColor());
    }

    /**
     * Создает компонент для отображения типа порчи
     *
     * @param itemStack Стек предметов
     * @return Форматированный компонент
     */
    public static Component createSpoilageTypeComponent(ItemStack itemStack) {
        TimedFoodManager.SpoiledType spoiledType = TimedFoodManager.getSpoiledType(itemStack.getItem());
        String typeKey = switch (spoiledType) {
            case ROTTEN_FLESH -> "tooltip.metaphysicsspoilage.spoilage_type.rotten_flesh";
            case COMPOST -> "tooltip.metaphysicsspoilage.spoilage_type.compost";
            case RANCID_OIL -> "tooltip.metaphysicsspoilage.spoilage_type.rancid_oil";
        };

        return Component.translatable("tooltip.metaphysicsspoilage.spoilage_type_label")
                .append(": ")
                .append(Component.translatable(typeKey))
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Создает полный тултип для еды с временной меткой
     *
     * @param itemStack Стек предметов
     * @param level Игровой мир
     * @return Массив компонентов для тултипа
     */
    public static Component[] createFoodTooltip(ItemStack itemStack, Level level) {
        if (!SpoilageUtils.hasTimestamp(itemStack)) {
            return new Component[0];
        }

        if (level == null) {
            level = Minecraft.getInstance().level;
        }

        if (level == null) {
            return new Component[0];
        }

        long creationDay = SpoilageUtils.getCreationDay(itemStack);
        if (creationDay == -1) {
            return new Component[0];
        }

        // Получаем информацию о времени
        long currentDay = level.getDayTime() / 24000L; // Приблизительный расчет дня
        long spoilageTime = SpoilageUtils.getSpoilageTime(itemStack.getItem());

        if (spoilageTime == -1) {
            return new Component[0];
        }

        long spoilageDay = creationDay + spoilageTime;
        long daysRemaining = spoilageDay - currentDay;

        // Определяем свежесть
        FreshnessLevel freshness = calculateFreshness(daysRemaining, spoilageTime);

        // Создаем компоненты тултипа
        Component[] tooltip = new Component[5];
        tooltip[0] = createCreationDayComponent(creationDay);
        tooltip[1] = createSpoilageDayComponent(spoilageDay);
        tooltip[2] = createDaysRemainingComponent(daysRemaining, freshness);
        tooltip[3] = createFreshnessComponent(freshness);
        tooltip[4] = createSpoilageTypeComponent(itemStack);

        return tooltip;
    }

    /**
     * Создает компактный тултип для еды (сокращенная версия)
     *
     * @param itemStack Стек предметов
     * @param level Игровой мир
     * @return Массив компонентов для тултипа
     */
    public static Component[] createCompactFoodTooltip(ItemStack itemStack, Level level) {
        if (!SpoilageUtils.hasTimestamp(itemStack)) {
            return new Component[0];
        }

        if (level == null) {
            level = Minecraft.getInstance().level;
        }

        if (level == null) {
            return new Component[0];
        }

        long creationDay = SpoilageUtils.getCreationDay(itemStack);
        if (creationDay == -1) {
            return new Component[0];
        }

        long currentDay = level.getDayTime() / 24000L; // Приблизительный расчет дня
        long spoilageTime = SpoilageUtils.getSpoilageTime(itemStack.getItem());

        if (spoilageTime == -1) {
            return new Component[0];
        }

        long daysRemaining = (creationDay + spoilageTime) - currentDay;
        FreshnessLevel freshness = calculateFreshness(daysRemaining, spoilageTime);

        // Компактная версия - только дни до порчи и состояние
        Component[] tooltip = new Component[2];
        tooltip[0] = createDaysRemainingComponent(daysRemaining, freshness);
        tooltip[1] = createFreshnessComponent(freshness);

        return tooltip;
    }

    /**
     * Проверяет, должны ли отображаться тултипы согласно конфигурации
     *
     * @return true если тултипы включены
     */
    public static boolean shouldShowTooltips() {
        return Config.ENABLE_SPOILAGE_SYSTEM.get() && Config.ENABLE_TOOLTIP_SYSTEM.get();
    }

    /**
     * Проверяет, должен ли отображаться расширенный тултип согласно конфигурации
     *
     * @return true если расширенные тултипы включены
     */
    public static boolean shouldShowDetailedTooltips() {
        return shouldShowTooltips() && Config.SHOW_DETAILED_TOOLTIPS.get();
    }

    /**
     * Форматирует время в читаемом формате
     *
     * @param days Количество дней
     * @return Отформатированная строка
     */
    public static String formatTimeRemaining(long days) {
        if (days <= 0) {
            return "0"; // Испорчено
        } else if (days == 1) {
            return "1"; // 1 день
        } else {
            return String.valueOf(days); // X дней
        }
    }

    /**
     * Создает разделитель для тултипа
     *
     * @return Компонент-разделитель
     */
    public static Component createSeparator() {
        return Component.literal("─────────────────").withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Создает заголовок для секции тултипа
     *
     * @param titleKey Ключ локализации заголовка
     * @return Форматированный заголовок
     */
    public static Component createSectionHeader(String titleKey) {
        return Component.translatable(titleKey).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }
}
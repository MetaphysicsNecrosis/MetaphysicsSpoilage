package com.metaphysicsnecrosis.metaphysicsspoilage.tooltip;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Обработчик событий тултипов для системы порчи еды.
 * Автоматически добавляет информацию о свежести, сроке годности и других
 * параметрах порчи к тултипам еды с временными метками.
 */
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID, value = Dist.CLIENT)
public class SpoilageTooltipHandler {

    /**
     * Обрабатывает событие создания тултипа для предметов
     *
     * @param event Событие тултипа
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        Level level = event.getEntity() != null ? event.getEntity().level() : Minecraft.getInstance().level;

        // Проверяем, нужно ли показывать тултипы
        if (!TooltipUtils.shouldShowTooltips()) {
            return;
        }

        // Проверяем, является ли предмет FoodContainer
        if (itemStack.getItem() instanceof FoodContainer) {
            handleFoodContainerTooltip(itemStack, tooltip, level, event.getFlags());
            return;
        }

        // Проверяем, является ли предмет едой с временной меткой
        if (SpoilageUtils.hasTimestamp(itemStack) && SpoilageUtils.canItemSpoil(itemStack.getItem())) {
            handleTimedFoodTooltip(itemStack, tooltip, level, event.getFlags());
        }
    }

    /**
     * Обрабатывает тултип для еды с временной меткой
     *
     * @param itemStack Стек предметов
     * @param tooltip Список компонентов тултипа
     * @param level Игровой мир
     * @param flags Флаги тултипа
     */
    private static void handleTimedFoodTooltip(ItemStack itemStack, List<Component> tooltip, Level level, TooltipFlag flags) {
        // Добавляем разделитель перед информацией о порче
        tooltip.add(TooltipUtils.createSeparator());

        // Добавляем заголовок секции
        tooltip.add(TooltipUtils.createSectionHeader("tooltip.metaphysicsspoilage.spoilage_info"));

        Component[] spoilageTooltip;

        // Выбираем тип тултипа в зависимости от настроек и флагов
        if (TooltipUtils.shouldShowDetailedTooltips() || flags.isAdvanced()) {
            spoilageTooltip = TooltipUtils.createFoodTooltip(itemStack, level);
        } else {
            spoilageTooltip = TooltipUtils.createCompactFoodTooltip(itemStack, level);
        }

        // Добавляем компоненты тултипа
        for (Component component : spoilageTooltip) {
            tooltip.add(component);
        }

        // Информация о порче уже отображается в TooltipUtils через цветовое кодирование
        // Дополнительная проверка через ServerLevel не нужна в tooltip'е
    }

    /**
     * Обрабатывает тултип для FoodContainer
     *
     * @param itemStack Стек предметов
     * @param tooltip Список компонентов тултипа
     * @param level Игровой мир
     * @param flags Флаги тултипа
     */
    private static void handleFoodContainerTooltip(ItemStack itemStack, List<Component> tooltip, Level level, TooltipFlag flags) {
        List<StoredFoodEntry> storedFood = itemStack.get(MetaphysicsSpoilage.STORED_FOOD_LIST.get());

        if (storedFood == null || storedFood.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // Добавляем разделитель
        tooltip.add(TooltipUtils.createSeparator());

        // Заголовок для содержимого контейнера
        tooltip.add(TooltipUtils.createSectionHeader("tooltip.metaphysicsspoilage.food_container.contents"));

        // Показываем общую информацию
        tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.item_count", storedFood.size())
                .withStyle(ChatFormatting.YELLOW));

        if (TooltipUtils.shouldShowDetailedTooltips() || flags.isAdvanced()) {
            // Детальная информация о содержимом
            handleDetailedContainerTooltip(storedFood, tooltip, level);
        } else {
            // Краткая информация о содержимом
            handleCompactContainerTooltip(storedFood, tooltip, level);
        }
    }

    /**
     * Обрабатывает детальный тултип для FoodContainer
     *
     * @param storedFood Список хранимой еды
     * @param tooltip Список компонентов тултипа
     * @param level Игровой мир
     */
    private static void handleDetailedContainerTooltip(List<StoredFoodEntry> storedFood, List<Component> tooltip, Level level) {
        // Показываем до 5 первых предметов
        int maxItems = Math.min(5, storedFood.size());

        for (int i = 0; i < maxItems; i++) {
            StoredFoodEntry entry = storedFood.get(i);
            ItemStack entryStack = entry.createItemStack();

            if (!entryStack.isEmpty() && SpoilageUtils.hasTimestamp(entryStack)) {
                Component[] entryTooltip = TooltipUtils.createCompactFoodTooltip(entryStack, level);
                if (entryTooltip.length > 0) {
                    tooltip.add(Component.literal("• ")
                            .append(entryStack.getDisplayName())
                            .append(" x")
                            .append(String.valueOf(entry.count()))
                            .withStyle(ChatFormatting.WHITE));

                    // Добавляем состояние свежести
                    if (entryTooltip.length > 1) {
                        tooltip.add(Component.literal("  ")
                                .append(entryTooltip[1]) // Состояние свежести
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }

        // Если есть еще предметы, показываем счетчик
        if (storedFood.size() > maxItems) {
            int remaining = storedFood.size() - maxItems;
            tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.more_items", remaining)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Обрабатывает компактный тултип для FoodContainer
     *
     * @param storedFood Список хранимой еды
     * @param tooltip Список компонентов тултипа
     * @param level Игровой мир
     */
    private static void handleCompactContainerTooltip(List<StoredFoodEntry> storedFood, List<Component> tooltip, Level level) {
        // Анализируем состояние еды в контейнере
        int freshCount = 0;
        int staleCount = 0;
        int spoiledCount = 0;

        for (StoredFoodEntry entry : storedFood) {
            ItemStack entryStack = entry.createItemStack();
            if (!entryStack.isEmpty() && SpoilageUtils.hasTimestamp(entryStack)) {
                long spoilageTime = SpoilageUtils.getSpoilageTime(entryStack.getItem());
                long creationDay = SpoilageUtils.getCreationDay(entryStack);

                if (spoilageTime != -1 && creationDay != -1 && level != null) {
                    long currentDay = level.getDayTime() / 24000L; // Приблизительный расчет дня
                    long daysRemaining = (creationDay + spoilageTime) - currentDay;

                    TooltipUtils.FreshnessLevel freshness = TooltipUtils.calculateFreshness(daysRemaining, spoilageTime);

                    switch (freshness) {
                        case FRESH -> freshCount += entry.count();
                        case SLIGHTLY_STALE, STALE -> staleCount += entry.count();
                        case SPOILED -> spoiledCount += entry.count();
                    }
                }
            }
        }

        // Показываем статистику
        if (freshCount > 0) {
            tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.fresh_items", freshCount)
                    .withStyle(ChatFormatting.GREEN));
        }

        if (staleCount > 0) {
            tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.stale_items", staleCount)
                    .withStyle(ChatFormatting.YELLOW));
        }

        if (spoiledCount > 0) {
            tooltip.add(Component.translatable("tooltip.metaphysicsspoilage.food_container.spoiled_items", spoiledCount)
                    .withStyle(ChatFormatting.RED));
        }
    }
}
package com.metaphysicsnecrosis.metaphysicsspoilage.manager;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс интеграции TimedFoodManager с игровыми событиями.
 * Предоставляет методы для автоматической обработки еды при различных событиях.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class TimedFoodManagerIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimedFoodManagerIntegration.class);

    /**
     * Обрабатывает предметы при подборе игроком
     *
     * @param player игрок, который подбирает предмет
     * @param itemEntity сущность предмета
     * @return обработанный ItemStack или null если обработка не требуется
     */
    public static ItemStack onItemPickup(Player player, ItemEntity itemEntity) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        ItemStack original = itemEntity.getItem();
        if (original.isEmpty()) {
            return null;
        }

        // Проверяем, нужно ли обрабатывать этот предмет
        if (!TimedFoodManager.canSpoil(original.getItem())) {
            return null;
        }

        // Если уже есть временная метка, не обрабатываем
        if (TimedFoodManager.isTimedFood(original)) {
            return null;
        }

        LOGGER.debug("Обработка подбора предмета {} игроком {}",
                original.getHoverName().getString(), player.getName().getString());

        return TimedFoodManager.processFood(original, serverLevel);
    }

    /**
     * Обрабатывает результаты крафта
     *
     * @param craftedStack результат крафта
     * @param level игровой мир
     * @return обработанный ItemStack
     */
    public static ItemStack onItemCrafted(ItemStack craftedStack, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return craftedStack;
        }

        if (craftedStack.isEmpty()) {
            return craftedStack;
        }

        // Проверяем, нужно ли обрабатывать этот предмет
        if (!TimedFoodManager.canSpoil(craftedStack.getItem())) {
            return craftedStack;
        }

        LOGGER.debug("Обработка скрафченного предмета: {}", craftedStack.getHoverName().getString());

        return TimedFoodManager.processFood(craftedStack, serverLevel);
    }

    /**
     * Обрабатывает предметы при торговле с жителями
     *
     * @param tradedStack предмет от торговли
     * @param level игровой мир
     * @return обработанный ItemStack
     */
    public static ItemStack onItemTraded(ItemStack tradedStack, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return tradedStack;
        }

        if (tradedStack.isEmpty()) {
            return tradedStack;
        }

        // Проверяем, нужно ли обрабатывать этот предмет
        if (!TimedFoodManager.canSpoil(tradedStack.getItem())) {
            return tradedStack;
        }

        LOGGER.debug("Обработка предмета от торговли: {}", tradedStack.getHoverName().getString());

        return TimedFoodManager.processFood(tradedStack, serverLevel);
    }

    /**
     * Обрабатывает предметы из лута (сундуки, мобы и т.д.)
     *
     * @param lootStack предмет из лута
     * @param level игровой мир
     * @return обработанный ItemStack
     */
    public static ItemStack onLootGenerated(ItemStack lootStack, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return lootStack;
        }

        if (lootStack.isEmpty()) {
            return lootStack;
        }

        // Проверяем, нужно ли обрабатывать этот предмет
        if (!TimedFoodManager.canSpoil(lootStack.getItem())) {
            return lootStack;
        }

        LOGGER.debug("Обработка предмета из лута: {}", lootStack.getHoverName().getString());

        return TimedFoodManager.processFood(lootStack, serverLevel);
    }

    /**
     * Проверяет и обрабатывает порчу в инвентаре игрока
     *
     * @param player игрок
     * @return список изменненых слотов инвентаря
     */
    public static List<Integer> checkPlayerInventorySpoilage(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        List<Integer> changedSlots = new ArrayList<>();
        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack processed = TimedFoodManager.checkAndProcessSpoilage(stack, serverLevel);
            if (!ItemStack.matches(stack, processed)) {
                inventory.setItem(i, processed);
                changedSlots.add(i);

                LOGGER.debug("Обработана порча в слоте {} игрока {}: {} -> {}",
                        i, player.getName().getString(),
                        stack.getHoverName().getString(),
                        processed.isEmpty() ? "исчез" : processed.getHoverName().getString());
            }
        }

        return changedSlots;
    }

    /**
     * Проверяет, можно ли съесть предмет согласно настройкам блокировки
     *
     * @param foodStack предмет еды
     * @return true, если предмет можно съесть
     */
    public static boolean canEatFood(ItemStack foodStack) {
        if (foodStack.isEmpty()) {
            return false;
        }

        String itemId = foodStack.getItem().toString();

        // Проверяем белый список всегда съедобных предметов
        if (Config.isAlwaysEdible(itemId)) {
            return true;
        }

        // Если система порчи отключена, разрешаем есть всё
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return true;
        }

        // Если предмет не может портиться, разрешаем есть
        if (!TimedFoodManager.canSpoil(foodStack.getItem())) {
            return true;
        }

        // Если у предмета есть временная метка, разрешаем есть
        if (TimedFoodManager.isTimedFood(foodStack)) {
            return true;
        }

        // Иначе проверяем режим блокировки
        Config.FoodBlockingMode mode = Config.FOOD_BLOCKING_MODE.get();
        return mode != Config.FoodBlockingMode.FULL_BLOCK;
    }

    /**
     * Получает модифицированные значения питания для еды без временной метки
     *
     * @param foodStack предмет еды
     * @param originalNutrition оригинальное питание
     * @param originalSaturation оригинальная сытость
     * @return массив [nutrition, saturation] или null если не нужно модифицировать
     */
    public static float[] getModifiedNutrition(ItemStack foodStack, int originalNutrition, float originalSaturation) {
        if (foodStack.isEmpty()) {
            return null;
        }

        String itemId = foodStack.getItem().toString();

        // Проверяем белый список всегда съедобных предметов
        if (Config.isAlwaysEdible(itemId)) {
            return null; // Не модифицируем
        }

        // Если система порчи отключена, не модифицируем
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return null;
        }

        // Если предмет не может портиться, не модифицируем
        if (!TimedFoodManager.canSpoil(foodStack.getItem())) {
            return null;
        }

        // Если у предмета есть временная метка, не модифицируем
        if (TimedFoodManager.isTimedFood(foodStack)) {
            return null;
        }

        // Проверяем режим блокировки
        Config.FoodBlockingMode mode = Config.FOOD_BLOCKING_MODE.get();
        if (mode == Config.FoodBlockingMode.ZERO_NUTRITION) {
            return new float[]{0.0f, 0.0f}; // Нулевое питание
        }

        return null; // Не модифицируем в других случаях
    }

    /**
     * Обрабатывает массовое обновление предметов в контейнере
     *
     * @param items список предметов
     * @param level игровой мир
     * @return обработанный список предметов
     */
    public static List<ItemStack> processBulkItems(List<ItemStack> items, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return items;
        }

        List<ItemStack> processed = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) {
                processed.add(item);
                continue;
            }

            if (TimedFoodManager.canSpoil(item.getItem()) && !TimedFoodManager.isTimedFood(item)) {
                processed.add(TimedFoodManager.processFood(item, serverLevel));
            } else {
                processed.add(item);
            }
        }

        return processed;
    }

    /**
     * Получает детальную информацию о предмете для отображения в тултипе
     *
     * @param stack предмет
     * @param level игровой мир
     * @return информация о сроке годности и состоянии
     */
    public static SpoilageTooltipInfo getTooltipInfo(ItemStack stack, Level level) {
        if (stack.isEmpty()) {
            return SpoilageTooltipInfo.EMPTY;
        }

        if (!TimedFoodManager.canSpoil(stack.getItem())) {
            return SpoilageTooltipInfo.NON_SPOILABLE;
        }

        if (!TimedFoodManager.isTimedFood(stack)) {
            return SpoilageTooltipInfo.NO_TIMESTAMP;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return SpoilageTooltipInfo.UNKNOWN;
        }

        long daysUntilSpoilage = TimedFoodManager.getDaysUntilSpoilage(stack, serverLevel);
        return new SpoilageTooltipInfo(daysUntilSpoilage);
    }

    /**
     * Класс для хранения информации о сроке годности для тултипов
     */
    public static class SpoilageTooltipInfo {
        public static final SpoilageTooltipInfo EMPTY = new SpoilageTooltipInfo(-2);
        public static final SpoilageTooltipInfo NON_SPOILABLE = new SpoilageTooltipInfo(-3);
        public static final SpoilageTooltipInfo NO_TIMESTAMP = new SpoilageTooltipInfo(-4);
        public static final SpoilageTooltipInfo UNKNOWN = new SpoilageTooltipInfo(-5);

        private final long daysUntilSpoilage;

        public SpoilageTooltipInfo(long daysUntilSpoilage) {
            this.daysUntilSpoilage = daysUntilSpoilage;
        }

        public long getDaysUntilSpoilage() {
            return daysUntilSpoilage;
        }

        public boolean isEmpty() {
            return this == EMPTY;
        }

        public boolean isNonSpoilable() {
            return this == NON_SPOILABLE;
        }

        public boolean hasNoTimestamp() {
            return this == NO_TIMESTAMP;
        }

        public boolean isUnknown() {
            return this == UNKNOWN;
        }

        public boolean isSpoiled() {
            return daysUntilSpoilage == 0;
        }

        public boolean isExpiringSoon() {
            return daysUntilSpoilage > 0 && daysUntilSpoilage <= 1;
        }

        public boolean isFresh() {
            return daysUntilSpoilage > 1;
        }
    }
}
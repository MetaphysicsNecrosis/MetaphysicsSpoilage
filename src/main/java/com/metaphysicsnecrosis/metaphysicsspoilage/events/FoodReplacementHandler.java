package com.metaphysicsnecrosis.metaphysicsspoilage.events;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.effects.FoodBlockingUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Обработчик событий для автоматической замены ванильной еды на версии с временными метками.
 *
 * Этот класс реализует систему автозамены согласно требованиям README:
 * "При получении игроком ванильной еды автоматически заменять ее на вариант с текущим днем.
 * Перехватывать события подбора дропа крафта торговли и лута."
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID)
public class FoodReplacementHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodReplacementHandler.class);

    /**
     * FIFO кэш для проверки является ли предмет едой (ограничен 256 записями)
     */
    private static final Map<String, Boolean> FOOD_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Boolean>(128, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 256;
            }
        }
    );

    /**
     * FIFO кэш для исключенных предметов (ограничен 256 записями)
     */
    private static final Map<String, Boolean> EXCLUDED_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Boolean>(128, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 256;
            }
        }
    );

    /**
     * Статистика замен для отладки
     */
    private static long replacementCount = 0;

    /**
     * Обрабатывает событие появления предметов в мире (подбор дропа)
     * Заменяет ванильную еду на версии с временными метками
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemEntityJoinWorld(EntityJoinLevelEvent event) {
        if (!shouldProcessEvent(event.getLevel())) {
            return;
        }

        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ItemStack originalStack = itemEntity.getItem();
            if (shouldReplaceItemStack(originalStack)) {
                ServerLevel level = (ServerLevel) event.getLevel();
                ItemStack replacedStack = processAndReplaceFood(originalStack, level);

                if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(originalStack, replacedStack)) {
                    // Замена произошла - логируем и обновляем ItemEntity
                    logReplacement("ItemDrop", originalStack, replacedStack);
                    itemEntity.setItem(replacedStack);
                }
            }
        }
    }

    /**
     * Обрабатывает событие крафта предметов
     * Заменяет скрафченную еду на версии с временными метками в инвентаре игрока
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerCraftedItem(PlayerEvent.ItemCraftedEvent event) {
        if (!shouldProcessEvent(event.getEntity().level())) {
            return;
        }

        ItemStack craftedStack = event.getCrafting();
        if (shouldReplaceItemStack(craftedStack)) {
            ServerLevel level = (ServerLevel) event.getEntity().level();
            Player player = event.getEntity();

            // Поскольку event.getCrafting() read-only, обрабатываем инвентарь игрока в следующем тике
            level.getServer().execute(() -> {
                processCraftedItemInInventory(player, craftedStack, level, "PlayerCrafting");
            });
        }
    }

    /**
     * Обрабатывает событие выплавки предметов
     * Заменяет выплавленную еду на версии с временными метками в инвентаре игрока
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!shouldProcessEvent(event.getEntity().level())) {
            return;
        }

        ItemStack smeltedStack = event.getSmelting();
        if (shouldReplaceItemStack(smeltedStack)) {
            ServerLevel level = (ServerLevel) event.getEntity().level();
            Player player = event.getEntity();

            // Поскольку event.getSmelting() read-only, обрабатываем инвентарь игрока в следующем тике
            level.getServer().execute(() -> {
                processCraftedItemInInventory(player, smeltedStack, level, "PlayerSmelting");
            });
        }
    }

    // TODO: Добавить обработку ItemPickupEvent когда найдем правильное событие в NeoForge 1.21
    // Пока полагаемся на периодические проверки инвентаря в SpoilageEventHandler

    /**
     * Обрабатывает взаимодействие игрока с контейнерами (для лута)
     * Автозамена еды при получении из сундуков, дропа мобов и т.д.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!shouldProcessEvent(event.getEntity().level())) {
            return;
        }

        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel level) {
            // Обрабатываем инвентарь игрока после взаимодействия
            // Это отсроченная обработка, выполняется в следующем тике
            level.getServer().execute(() -> {
                processPlayerInventory(player, level, "ContainerInteraction");
            });
        }
    }

    /**
     * Обработчик тиков сервера для периодической очистки кэша и обработки контейнеров
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Очищаем кэш каждые 1200 тиков (1 минута)
        if (event.getServer().getTickCount() % 1200 == 0) {
            clearCaches();
        }

        // Проверяем открытые контейнеры игроков с настраиваемым интервалом
        if (event.getServer().getTickCount() % Config.PLAYER_CONTAINER_CHECK_INTERVAL.get() == 0) {
            processPlayerContainers(event.getServer());
        }

        // Опциональная глобальная проверка всех контейнеров в загруженных чанках
        if (Config.ENABLE_GLOBAL_CONTAINER_PROCESSING.get() &&
            event.getServer().getTickCount() % Config.GLOBAL_CONTAINER_CHECK_INTERVAL.get() == 0) {
            processGlobalContainers(event.getServer());
        }
    }

    /**
     * Обрабатывает выгрузку мира для очистки данных
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) {
            clearCaches();
            LOGGER.debug("Кэш FoodReplacementHandler очищен при выгрузке мира");
        }
    }

    /**
     * Проверяет, должно ли событие обрабатываться
     */
    private static boolean shouldProcessEvent(net.minecraft.world.level.Level level) {
        // Обрабатываем только серверные события
        if (level != null && level.isClientSide) {
            return false;
        }

        // Проверяем что система порчи включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return false;
        }

        return true;
    }

    /**
     * Проверяет, должен ли ItemStack быть заменен
     */
    private static boolean shouldReplaceItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Быстрая проверка - если уже есть временная метка, не заменяем
        if (SpoilageUtils.hasTimestamp(stack)) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверка исключений из кэша
        Boolean isExcluded = EXCLUDED_CACHE.get(itemId);
        if (isExcluded == null) {
            isExcluded = Config.isItemExcluded(itemId);
            EXCLUDED_CACHE.put(itemId, isExcluded);
        }

        if (isExcluded) {
            return false;
        }

        // Проверка является ли еда из кэша
        Boolean isFood = FOOD_CACHE.get(itemId);
        if (isFood == null) {
            isFood = SpoilageUtils.canItemSpoil(stack.getItem());
            FOOD_CACHE.put(itemId, isFood);
        }

        return isFood;
    }

    /**
     * Обрабатывает и заменяет еду на версию с временной меткой
     */
    private static ItemStack processAndReplaceFood(ItemStack original, ServerLevel level) {
        if (original.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try {
            // Создаем копию стека
            ItemStack processed = original.copy();

            // Обрабатываем каждый предмет в стеке
            if (processed.getCount() > 1) {
                // Для стеков > 1 обрабатываем весь стек
                SpoilageUtils.autoSetTimestamp(processed, level);
            } else {
                // Для единичных предметов используем TimedFoodManager
                processed = TimedFoodManager.processFood(processed, level);
            }

            // Применяем FoodBlockingConsumeEffect к предметам без временной метки
            if (!SpoilageUtils.hasTimestamp(processed)) {
                FoodBlockingUtils.applyFoodBlockingEffect(processed);
                // FoodBlockingConsumeEffect применён
            }

            return processed;

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке замены еды для предмета: {}",
                    BuiltInRegistries.ITEM.getKey(original.getItem()), e);
            return original; // Возвращаем оригинал при ошибке
        }
    }

    /**
     * Обрабатывает подобранную еду в инвентаре игрока
     * Ищет и заменяет подобранные предметы без временной метки на версии с меткой
     */
    private static void processPickedItemInInventory(Player player, ItemStack pickedStack, ServerLevel level) {
        try {
            String pickedItemId = BuiltInRegistries.ITEM.getKey(pickedStack.getItem()).toString();
            boolean foundAndReplaced = false;

            // Ищем в инвентаре игрока недавно подобранные предметы того же типа без временной метки
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack inventoryStack = player.getInventory().getItem(i);

                if (inventoryStack.isEmpty()) {
                    continue;
                }

                String inventoryItemId = BuiltInRegistries.ITEM.getKey(inventoryStack.getItem()).toString();

                // Проверяем что это тот же предмет и у него нет временной метки
                if (pickedItemId.equals(inventoryItemId) && !SpoilageUtils.hasTimestamp(inventoryStack)) {
                    ItemStack replacedStack = processAndReplaceFood(inventoryStack, level);

                    if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(inventoryStack, replacedStack)) {
                        player.getInventory().setItem(i, replacedStack);
                        logReplacement("ItemPickup_Inventory", inventoryStack, replacedStack);
                        foundAndReplaced = true;

                        LOGGER.debug("Заменен подобранный предмет {} в слоте {} инвентаря игрока {}",
                                inventoryItemId, i, player.getName().getString());
                        break; // Заменяем только первый найденный стек
                    }
                }
            }

            if (!foundAndReplaced) {
                LOGGER.debug("Не найдены подобранные предметы {} без временной метки в инвентаре игрока {}",
                        pickedItemId, player.getName().getString());
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке подобранного предмета в инвентаре", e);
        }
    }

    /**
     * Обрабатывает скрафченную/выплавленную еду в инвентаре игрока
     * Ищет и заменяет предметы без временной метки на версии с меткой
     */
    private static void processCraftedItemInInventory(Player player, ItemStack craftedStack, ServerLevel level, String context) {
        try {
            String craftedItemId = BuiltInRegistries.ITEM.getKey(craftedStack.getItem()).toString();
            boolean foundAndReplaced = false;

            // Ищем в инвентаре игрока предметы того же типа без временной метки
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack inventoryStack = player.getInventory().getItem(i);

                if (inventoryStack.isEmpty()) {
                    continue;
                }

                String inventoryItemId = BuiltInRegistries.ITEM.getKey(inventoryStack.getItem()).toString();

                // Проверяем что это тот же предмет и у него нет временной метки
                if (craftedItemId.equals(inventoryItemId) && !SpoilageUtils.hasTimestamp(inventoryStack)) {
                    ItemStack replacedStack = processAndReplaceFood(inventoryStack, level);

                    if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(inventoryStack, replacedStack)) {
                        player.getInventory().setItem(i, replacedStack);
                        logReplacement(context + "_Inventory", inventoryStack, replacedStack);
                        foundAndReplaced = true;

                        LOGGER.debug("Заменен предмет {} в слоте {} инвентаря игрока {} (контекст: {})",
                                inventoryItemId, i, player.getName().getString(), context);
                    }
                }
            }

            if (!foundAndReplaced) {
                LOGGER.debug("Не найдены предметы {} без временной метки в инвентаре игрока {} (контекст: {})",
                        craftedItemId, player.getName().getString(), context);
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке скрафченного предмета в инвентаре (контекст: {})", context, e);
        }
    }

    /**
     * Логирует замену предмета для отладки
     */
    private static void logReplacement(String context, ItemStack original, ItemStack replaced) {
        replacementCount++;

        String originalId = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
        long creationDay = SpoilageUtils.hasTimestamp(replaced) ?
                SpoilageUtils.getCreationDay(replaced) : -1;

        // Замена произведена
    }

    /**
     * Очищает кэши для производительности
     */
    private static void clearCaches() {
        int foodCacheSize = FOOD_CACHE.size();
        int excludedCacheSize = EXCLUDED_CACHE.size();

        FOOD_CACHE.clear();
        EXCLUDED_CACHE.clear();

        if (foodCacheSize > 0 || excludedCacheSize > 0) {
            LOGGER.debug("Очищены кэши FoodReplacementHandler: food={}, excluded={}",
                    foodCacheSize, excludedCacheSize);
        }
    }

    /**
     * Получает статистику работы системы замены
     */
    public static String getReplacementStats() {
        return String.format("Замен выполнено: %d, Кэш еды: %d, Кэш исключений: %d",
                replacementCount, FOOD_CACHE.size(), EXCLUDED_CACHE.size());
    }

    /**
     * Сбрасывает статистику (для тестирования)
     */
    public static void resetStats() {
        replacementCount = 0;
        clearCaches();
        LOGGER.info("Статистика FoodReplacementHandler сброшена");
    }

    /**
     * Валидирует корректность работы обработчика
     */
    public static boolean validateHandler() {
        try {
            // Проверяем что Config доступен
            boolean systemEnabled = Config.ENABLE_SPOILAGE_SYSTEM.get();

            // Проверяем что TimedFoodManager работает
            if (!TimedFoodManager.validateManager()) {
                LOGGER.error("TimedFoodManager не прошел валидацию");
                return false;
            }

            // Проверяем что FoodBlockingUtils работает
            if (!FoodBlockingUtils.validateUtils()) {
                LOGGER.error("FoodBlockingUtils не прошел валидацию");
                return false;
            }

            // Проверяем что SpoilageUtils доступен
            ItemStack testStack = new ItemStack(Items.APPLE);
            boolean canSpoil = SpoilageUtils.canItemSpoil(Items.APPLE);

            LOGGER.info("Валидация FoodReplacementHandler прошла успешно. Система включена: {}",
                    systemEnabled);
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации FoodReplacementHandler", e);
            return false;
        }
    }

    /**
     * Принудительно обрабатывает ItemStack (для ручного использования)
     */
    public static ItemStack forceProcessItemStack(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty() || level == null) {
            return stack;
        }

        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            LOGGER.debug("Система порчи отключена, принудительная обработка пропущена");
            return stack;
        }

        if (shouldReplaceItemStack(stack)) {
            ItemStack processed = processAndReplaceFood(stack, level);
            if (!processed.isEmpty()) {
                logReplacement("ForceProcess", stack, processed);
                return processed;
            }
        } else {
            // Даже если не требуется замена, применяем блокировку к еде без временной метки
            FoodBlockingUtils.applyFoodBlockingEffect(stack);
        }

        return stack;
    }

    /**
     * Проверяет, подходит ли предмет для автозамены
     */
    public static boolean isItemEligibleForReplacement(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Не заменяем если уже есть временная метка
        if (SpoilageUtils.hasTimestamp(stack)) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем исключения
        if (Config.isItemExcluded(itemId)) {
            return false;
        }

        // Проверяем может ли портиться
        return SpoilageUtils.canItemSpoil(stack.getItem());
    }

    /**
     * Обрабатывает инвентарь игрока для автозамены еды
     */
    private static void processPlayerInventory(Player player, ServerLevel level, String context) {
        try {
            boolean hasReplacements = false;

            // Обрабатываем основной инвентарь
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (shouldReplaceItemStack(stack)) {
                    ItemStack replacedStack = processAndReplaceFood(stack, level);

                    if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, replacedStack)) {
                        player.getInventory().setItem(i, replacedStack);
                        logReplacement(context + "_Inventory", stack, replacedStack);
                        hasReplacements = true;
                    }
                }
            }

            if (hasReplacements) {
                LOGGER.debug("Обработан инвентарь игрока {} в контексте {}",
                    player.getName().getString(), context);
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке инвентаря игрока в контексте {}", context, e);
        }
    }

    /**
     * Обрабатывает стеки с количеством больше 1
     */
    private static void processStackItems(ItemStack stack, ServerLevel level, String context) {
        if (stack.getCount() <= 1) {
            return;
        }

        try {
            // Для стеков > 1 устанавливаем временную метку на весь стек
            if (shouldReplaceItemStack(stack)) {
                SpoilageUtils.autoSetTimestamp(stack, level);

                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                LOGGER.debug("Обработан стек x{} {} в контексте {}",
                    stack.getCount(), itemId, context);
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке стека в контексте {}", context, e);
        }
    }

    /**
     * Получает расширенную статистику работы системы
     */
    public static String getDetailedStats() {
        return String.format(
            "=== Статистика FoodReplacementHandler ===\n" +
            "Всего замен выполнено: %d\n" +
            "Размер кэша еды: %d\n" +
            "Размер кэша исключений: %d\n" +
            "Система порчи включена: %s\n" +
            "TimedFoodManager статус: %s\n" +
            "FoodBlockingUtils статус: %s\n" +
            "Режим блокировки еды: %s\n" +
            "=== Настройки контейнеров ===\n" +
            "Глобальная обработка: %s\n" +
            "Интервал глобальной проверки: %d тиков\n" +
            "Интервал проверки игроков: %d тиков",
            replacementCount,
            FOOD_CACHE.size(),
            EXCLUDED_CACHE.size(),
            Config.ENABLE_SPOILAGE_SYSTEM.get(),
            TimedFoodManager.validateManager() ? "OK" : "ERROR",
            FoodBlockingUtils.validateUtils() ? "OK" : "ERROR",
            Config.FOOD_BLOCKING_MODE.get().getName(),
            Config.ENABLE_GLOBAL_CONTAINER_PROCESSING.get() ? "Включена" : "Отключена",
            Config.GLOBAL_CONTAINER_CHECK_INTERVAL.get(),
            Config.PLAYER_CONTAINER_CHECK_INTERVAL.get()
        );
    }

    // === ОБРАБОТКА КОНТЕЙНЕРОВ В РЕАЛЬНОМ ВРЕМЕНИ ===

    /**
     * Обрабатывает все контейнеры в загруженных чанках (опционально)
     */
    private static void processGlobalContainers(net.minecraft.server.MinecraftServer server) {
        if (!shouldProcessEvent(null)) {
            return;
        }

        try {
            server.getAllLevels().forEach(level -> {
                ServerLevel serverLevel = (ServerLevel) level;

                // Простой подход - итерируемся по всем игрокам и обрабатываем область вокруг них
                serverLevel.players().forEach(player -> {
                    processContainersAroundPlayer(player, serverLevel);
                });
            });
        } catch (Exception e) {
            LOGGER.error("Ошибка при глобальной обработке контейнеров", e);
        }
    }

    /**
     * Обрабатывает контейнеры в области вокруг игрока
     */
    private static void processContainersAroundPlayer(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
        try {
            net.minecraft.core.BlockPos playerPos = player.blockPosition();
            int radius = 32; // 32 блока вокруг игрока

            // Ищем блок-энтити в области вокруг игрока
            for (int x = -radius; x <= radius; x += 16) { // Проверяем каждые 16 блоков для производительности
                for (int z = -radius; z <= radius; z += 16) {
                    for (int y = -16; y <= 16; y += 8) { // Меньший диапазон по Y
                        net.minecraft.core.BlockPos checkPos = playerPos.offset(x, y, z);

                        if (level.isLoaded(checkPos)) {
                            BlockEntity blockEntity = level.getBlockEntity(checkPos);
                            if (blockEntity != null) {
                                processBlockEntity(blockEntity, level);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Ошибка при обработке области вокруг игрока {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }

    /**
     * Обрабатывает конкретный блок-энтити
     */
    private static void processBlockEntity(BlockEntity blockEntity, ServerLevel level) {
        try {
            if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
                processFurnaceBlockEntity(furnace, level);
            } else if (blockEntity instanceof Container container) {
                processGenericContainer(container, level);
            }
        } catch (Exception e) {
            LOGGER.debug("Ошибка при обработке блок-энтити {}: {}",
                    blockEntity.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Обрабатывает результат в конкретной печи
     */
    private static void processFurnaceBlockEntity(AbstractFurnaceBlockEntity furnace, ServerLevel level) {
        try {
            // Проверяем слот результата печи (слот 2)
            ItemStack resultStack = furnace.getItem(2);

            if (!resultStack.isEmpty() && shouldReplaceItemStack(resultStack)) {
                ItemStack replacedStack = processAndReplaceFood(resultStack, level);

                if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(resultStack, replacedStack)) {
                    furnace.setItem(2, replacedStack);
                    logReplacement("GlobalFurnaceResult", resultStack, replacedStack);

                    LOGGER.debug("Глобально заменен результат в печи {} на версию с временной меткой",
                            BuiltInRegistries.ITEM.getKey(resultStack.getItem()));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Ошибка при обработке печи: {}", e.getMessage());
        }
    }

    /**
     * Обрабатывает обычный контейнер
     */
    private static void processGenericContainer(Container container, ServerLevel level) {
        try {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);

                if (!stack.isEmpty() && shouldReplaceItemStack(stack)) {
                    ItemStack replacedStack = processAndReplaceFood(stack, level);

                    if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, replacedStack)) {
                        container.setItem(i, replacedStack);
                        logReplacement("GlobalContainer", stack, replacedStack);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Ошибка при обработке контейнера: {}", e.getMessage());
        }
    }

    /**
     * Обрабатывает контейнеры открытые игроками
     */
    private static void processPlayerContainers(net.minecraft.server.MinecraftServer server) {
        if (!shouldProcessEvent(null)) {
            return;
        }

        try {
            server.getPlayerList().getPlayers().forEach(player -> {
                if (player.containerMenu != null && !(player.containerMenu instanceof net.minecraft.world.inventory.InventoryMenu)) {
                    processPlayerContainerMenu(player, (ServerLevel) player.level());
                }
            });
        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке контейнеров игроков", e);
        }
    }

    /**
     * Обрабатывает меню контейнера игрока
     */
    private static void processPlayerContainerMenu(net.minecraft.server.level.ServerPlayer player, ServerLevel level) {
        try {
            AbstractContainerMenu menu = player.containerMenu;

            // Обрабатываем слоты результата в различных типах меню
            if (menu instanceof FurnaceMenu furnaceMenu) {
                processFurnaceMenuResultSlot(furnaceMenu, level);
            } else if (menu instanceof CraftingMenu craftingMenu) {
                processCraftingMenuResultSlot(craftingMenu, level);
            }
            // Можно добавить обработку других типов меню

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке меню контейнера игрока {}",
                    player.getName().getString(), e);
        }
    }

    /**
     * Обрабатывает слот результата в меню печи
     */
    private static void processFurnaceMenuResultSlot(FurnaceMenu menu, ServerLevel level) {
        try {
            // Слот результата печи обычно имеет индекс 2
            Slot resultSlot = menu.getSlot(2);
            ItemStack resultStack = resultSlot.getItem();

            if (!resultStack.isEmpty() && shouldReplaceItemStack(resultStack)) {
                ItemStack replacedStack = processAndReplaceFood(resultStack, level);

                if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(resultStack, replacedStack)) {
                    resultSlot.set(replacedStack);
                    logReplacement("FurnaceMenuResult", resultStack, replacedStack);

                    LOGGER.debug("Заменен результат в меню печи на версию с временной меткой: {}",
                            BuiltInRegistries.ITEM.getKey(resultStack.getItem()));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке слота результата печи", e);
        }
    }

    /**
     * Обрабатывает слот результата в меню крафта
     */
    private static void processCraftingMenuResultSlot(CraftingMenu menu, ServerLevel level) {
        try {
            // Слот результата крафта обычно имеет индекс 0
            Slot resultSlot = menu.getSlot(0);
            ItemStack resultStack = resultSlot.getItem();

            if (!resultStack.isEmpty() && shouldReplaceItemStack(resultStack)) {
                ItemStack replacedStack = processAndReplaceFood(resultStack, level);

                if (!replacedStack.isEmpty() && !ItemStack.isSameItemSameComponents(resultStack, replacedStack)) {
                    resultSlot.set(replacedStack);
                    logReplacement("CraftingMenuResult", resultStack, replacedStack);

                    LOGGER.debug("Заменен результат в меню крафта на версию с временной меткой: {}",
                            BuiltInRegistries.ITEM.getKey(resultStack.getItem()));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке слота результата крафта", e);
        }
    }
}
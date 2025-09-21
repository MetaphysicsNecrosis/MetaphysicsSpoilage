package com.metaphysicsnecrosis.metaphysicsspoilage.gui;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Контейнер-меню для GUI FoodContainer.
 * Обрабатывает серверную логику и синхронизацию с клиентом.
 */
public class FoodContainerMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerMenu.class);

    private final ItemStack containerStack;
    private final Player player;
    private final int containerSlot;

    /**
     * Создает новое меню FoodContainer
     *
     * @param windowId ID окна
     * @param inventory Инвентарь игрока
     * @param containerStack ItemStack контейнера
     * @param containerSlot Слот контейнера в инвентаре игрока
     */
    public FoodContainerMenu(int windowId, Inventory inventory, ItemStack containerStack, int containerSlot) {
        super(MetaphysicsSpoilage.FOOD_CONTAINER_MENU.get(), windowId);
        this.containerStack = containerStack;
        this.player = inventory.player;
        this.containerSlot = containerSlot;

        LOGGER.debug("Создано FoodContainerMenu для игрока {} с контейнером в слоте {}",
                    player.getName().getString(), containerSlot);

        // Выполняем начальную синхронизацию при открытии меню
        if (!player.level().isClientSide()) {
            LOGGER.info("ОТКРЫТИЕ GUI КОНТЕЙНЕРА - принудительная проверка порчи");
            refreshContainer();
        }
    }

    /**
     * Создает меню только с инвентарем (для клиентской стороны)
     */
    public FoodContainerMenu(int windowId, Inventory inventory) {
        this(windowId, inventory, ItemStack.EMPTY, -1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Быстрое перемещение не поддерживается в данном GUI
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Проверяем, что игрок все еще может использовать контейнер
        if (containerSlot < 0 || containerSlot >= player.getInventory().getContainerSize()) {
            return false;
        }

        ItemStack currentStack = player.getInventory().getItem(containerSlot);
        return !currentStack.isEmpty() && currentStack.getItem() instanceof FoodContainer;
    }

    /**
     * Получает список сохраненной еды из контейнера
     */
    public List<StoredFoodEntry> getStoredFoods() {
        // На клиентской стороне используем синхронизированные данные
        if (player.level().isClientSide()) {
            synchronized (this) {
                // Если есть свежие синхронизированные данные (не старше 3 секунд), используем их
                if (lastSyncTime > 0 && (System.currentTimeMillis() - lastSyncTime) < 3000L) {
                    LOGGER.debug("Используются синхронизированные данные: {} записей", lastSyncedFoods.size());
                    return new ArrayList<>(lastSyncedFoods);
                }
            }
        }

        // На серверной стороне или если нет синхронизированных данных
        ItemStack currentContainer = getCurrentContainer();
        if (currentContainer.isEmpty() || !(currentContainer.getItem() instanceof FoodContainer)) {
            LOGGER.debug("Контейнер пустой или не является FoodContainer: isEmpty={}, item={}",
                currentContainer.isEmpty(),
                currentContainer.isEmpty() ? "null" : currentContainer.getItem().getClass().getSimpleName());
            return List.of();
        }
        List<StoredFoodEntry> foods = FoodContainer.getStoredFoods(currentContainer);
        LOGGER.debug("Получено {} записей из контейнера", foods.size());
        return foods;
    }

    /**
     * Получает текущий контейнер из инвентаря игрока
     */
    private ItemStack getCurrentContainer() {
        if (!containerStack.isEmpty()) {
            return containerStack;
        }

        // Ищем FoodContainer в инвентаре игрока
        Inventory inventory = player.getInventory();

        // Проверяем основную руку (текущий выбранный слот)
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof FoodContainer) {
            return mainHand;
        }

        // Проверяем вторую руку (слот 40)
        ItemStack offHand = player.getOffhandItem();
        if (!offHand.isEmpty() && offHand.getItem() instanceof FoodContainer) {
            return offHand;
        }

        // Ищем в хотбаре
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof FoodContainer) {
                return stack;
            }
        }

        LOGGER.warn("Не найден FoodContainer в инвентаре игрока {}", player.getName().getString());
        return ItemStack.EMPTY;
    }

    /**
     * Извлекает указанное количество еды из контейнера
     *
     * @param itemId ID предмета для извлечения
     * @param count Количество для извлечения
     * @return true если операция успешна
     */
    public boolean extractFood(String itemId, int count) {
        ItemStack currentContainer = getCurrentContainer();
        if (currentContainer.isEmpty() || !(currentContainer.getItem() instanceof FoodContainer)) {
            LOGGER.warn("Попытка извлечь еду из недействительного контейнера");
            return false;
        }

        if (player.level().isClientSide) {
            LOGGER.warn("Попытка извлечь еду на клиентской стороне");
            return false;
        }

        // Проверяем и удаляем испорченную еду перед извлечением
        if (player.level() instanceof ServerLevel serverLevel) {
            FoodContainer.checkAndRemoveSpoiledFood(currentContainer, serverLevel);
        }

        ItemStack extractedStack = FoodContainer.removeFood(currentContainer, itemId, count);
        if (!extractedStack.isEmpty()) {
            // Добавляем извлеченную еду в инвентарь игрока
            if (!player.getInventory().add(extractedStack)) {
                // Если инвентарь полон, выбрасываем предмет
                player.drop(extractedStack, false);
            }

            // Обновляем контейнер в инвентаре игрока
            player.getInventory().setItem(containerSlot, containerStack);

            LOGGER.debug("Игрок {} извлек {} x{} из контейнера",
                        player.getName().getString(), itemId, extractedStack.getCount());
            return true;
        }

        return false;
    }

    /**
     * Извлекает всю еду указанного типа из контейнера
     *
     * @param itemId ID предмета для извлечения
     * @return true если операция успешна
     */
    public boolean extractAllFood(String itemId) {
        if (containerStack.isEmpty() || !(containerStack.getItem() instanceof FoodContainer)) {
            return false;
        }

        // Находим общее количество еды данного типа
        List<StoredFoodEntry> storedFoods = FoodContainer.getStoredFoods(containerStack);
        int totalCount = storedFoods.stream()
                .filter(entry -> entry.itemId().equals(itemId))
                .mapToInt(StoredFoodEntry::count)
                .sum();

        if (totalCount > 0) {
            return extractFood(itemId, totalCount);
        }

        return false;
    }

    /**
     * Извлекает самую старую еду из контейнера
     *
     * @param count Количество для извлечения
     * @return true если операция успешна
     */
    public boolean extractOldestFood(int count) {
        if (containerStack.isEmpty() || !(containerStack.getItem() instanceof FoodContainer)) {
            return false;
        }

        if (player.level().isClientSide) {
            return false;
        }

        // Проверяем и удаляем испорченную еду перед извлечением
        if (player.level() instanceof ServerLevel serverLevel) {
            FoodContainer.checkAndRemoveSpoiledFood(containerStack, serverLevel);
        }

        ItemStack extractedStack = FoodContainer.extractOldestFood(containerStack, count);
        if (!extractedStack.isEmpty()) {
            // Добавляем извлеченную еду в инвентарь игрока
            if (!player.getInventory().add(extractedStack)) {
                // Если инвентарь полон, выбрасываем предмет
                player.drop(extractedStack, false);
            }

            // Обновляем контейнер в инвентаре игрока
            player.getInventory().setItem(containerSlot, containerStack);

            LOGGER.debug("Игрок {} извлек самую старую еду x{} из контейнера",
                        player.getName().getString(), extractedStack.getCount());
            return true;
        }

        return false;
    }

    /**
     * Получает общее количество предметов в контейнере
     */
    public int getTotalItemCount() {
        return FoodContainer.getTotalCount(containerStack);
    }

    /**
     * Обновляет содержимое контейнера (используется для синхронизации с клиентом)
     */
    public void updateContainerContents(List<StoredFoodEntry> foods) {
        // Обновляем кэш содержимого для последующих запросов getStoredFoods()
        // Этот метод вызывается только на клиентской стороне
        synchronized (this) {
            lastSyncedFoods = new ArrayList<>(foods);
            lastSyncTime = System.currentTimeMillis();
        }
        LOGGER.debug("Обновлено содержимое FoodContainer меню: {} записей", foods.size());
    }

    // Кэш для синхронизированных данных на клиенте
    private volatile List<StoredFoodEntry> lastSyncedFoods = new ArrayList<>();
    private volatile long lastSyncTime = 0;

    /**
     * Проверяет, пуст ли контейнер
     */
    public boolean isEmpty() {
        return FoodContainer.isEmpty(containerStack);
    }

    /**
     * Принудительно очищает кэш синхронизированных данных
     */
    public void clearSyncCache() {
        synchronized (this) {
            lastSyncedFoods.clear();
            lastSyncTime = 0;
        }
        LOGGER.debug("Очищен кэш синхронизированных данных");
    }

    /**
     * Получает ItemStack контейнера для отображения
     */
    public ItemStack getContainerStack() {
        return containerStack;
    }

    /**
     * Обновляет данные контейнера (для синхронизации с клиентом)
     */
    public void refreshContainer() {
        if (player.level() instanceof ServerLevel serverLevel) {
            FoodContainer.checkAndRemoveSpoiledFood(containerStack, serverLevel);

            // Отправляем обновленные данные клиенту
            syncContainerToClient();

            // Принудительно очищаем кэш на клиенте для немедленного обновления
            clearSyncCache();

            // Уведомляем меню об изменениях
            this.slotsChanged(null);
        }
    }

    /**
     * Синхронизирует содержимое контейнера с клиентом
     */
    private void syncContainerToClient() {
        if (player.level().isClientSide()) {
            return; // Выполняется только на сервере
        }

        try {
            List<StoredFoodEntry> currentFoods = FoodContainer.getStoredFoods(containerStack);
            FoodContainerSyncPayload syncPayload = new FoodContainerSyncPayload(currentFoods);

            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, syncPayload);
                LOGGER.info("Отправлены синхронизированные данные игроку {}: {} записей",
                    serverPlayer.getName().getString(), currentFoods.size());

                // Дополнительная отладочная информация
                for (StoredFoodEntry entry : currentFoods) {
                    LOGGER.debug("  - {} x{} (день {})", entry.itemId(), entry.count(), entry.creationDay());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при синхронизации контейнера с клиентом: {}", e.getMessage());
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        // Обновляем контейнер в инвентаре при закрытии GUI
        if (containerSlot >= 0 && containerSlot < player.getInventory().getContainerSize()) {
            player.getInventory().setItem(containerSlot, containerStack);
        }

        LOGGER.debug("FoodContainerMenu закрыто для игрока {}", player.getName().getString());
    }
}
package com.metaphysicsnecrosis.metaphysicsspoilage.items;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageTransformer;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
// import net.neoforged.neoforge.network.NetworkHooks; // Не используется в NeoForge 1.21.8
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Контейнер для хранения еды с временными метками.
 * Поддерживает разные уровни вместимости и ограничений.
 * Использует DataComponents для хранения списка StoredFoodEntry.
 */
public class FoodContainer extends Item {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainer.class);

    public enum ContainerTier {
        BASIC(16, 2, "basic"),       // 16 слотов, 2 типа еды
        ADVANCED(32, 4, "advanced"), // 32 слота, 4 типа еды
        PREMIUM(64, 8, "premium");   // 64 слота, 8 типов еды

        private final int maxSlots;
        private final int maxFoodTypes;
        private final String name;

        ContainerTier(int maxSlots, int maxFoodTypes, String name) {
            this.maxSlots = maxSlots;
            this.maxFoodTypes = maxFoodTypes;
            this.name = name;
        }

        public int getMaxSlots() { return maxSlots; }
        public int getMaxFoodTypes() { return maxFoodTypes; }
        public String getName() { return name; }
    }

    private final ContainerTier tier;

    // Ограничения
    private static final int MAX_STACK_SIZE = 64;

    public FoodContainer(Properties properties, ContainerTier tier) {
        super(properties);
        this.tier = tier;
    }

    public ContainerTier getTier() {
        return tier;
    }

    /**
     * Добавляет еду в контейнер
     *
     * @param container ItemStack контейнера (не может быть null)
     * @param food ItemStack еды для добавления (не может быть null)
     * @return true если еда была успешно добавлена
     */
    public static boolean addFood(ItemStack container, ItemStack food) {
        // Проверки безопасности на null
        if (container == null) {
            LOGGER.error("Контейнер не может быть null");
            return false;
        }
        if (food == null) {
            LOGGER.error("Еда не может быть null");
            return false;
        }

        if (container.isEmpty() || food.isEmpty()) {
            LOGGER.warn("Попытка добавить еду в пустой контейнер или добавить пустую еду");
            return false;
        }

        if (!(container.getItem() instanceof FoodContainer foodContainer)) {
            LOGGER.warn("Попытка добавить еду в предмет, который не является контейнером");
            return false;
        }

        // Проверяем, является ли предмет едой
        FoodProperties foodProperties = food.get(DataComponents.FOOD);
        if (foodProperties == null) {
            LOGGER.debug("Предмет {} не является едой", BuiltInRegistries.ITEM.getKey(food.getItem()));
            return false;
        }

        // Проверяем, есть ли временная метка у еды
        if (!SpoilageUtils.hasTimestamp(food)) {
            LOGGER.debug("Еда {} не имеет временной метки", BuiltInRegistries.ITEM.getKey(food.getItem()));
            return false;
        }

        // Получаем данные еды с дополнительными проверками безопасности
        String itemId;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(food.getItem()).toString();
        } catch (Exception e) {
            LOGGER.error("Ошибка получения ID предмета: {}", e.getMessage());
            return false;
        }

        long creationDay;
        try {
            creationDay = SpoilageUtils.getCreationDay(food);
        } catch (Exception e) {
            LOGGER.error("Ошибка получения дня создания еды: {}", e.getMessage());
            return false;
        }

        int addCount = food.getCount();
        if (addCount <= 0) {
            LOGGER.warn("Некорректное количество еды для добавления: {}", addCount);
            return false;
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(container);

        // Ищем существующую запись для объединения
        StoredFoodEntry existingEntry = null;
        for (StoredFoodEntry entry : storedFoods) {
            if (entry.itemId().equals(itemId) && entry.creationDay() == creationDay) {
                existingEntry = entry;
                break;
            }
        }

        if (existingEntry != null) {
            // Проверяем, не превысим ли максимальный размер стека
            if (existingEntry.count() + addCount > MAX_STACK_SIZE) {
                LOGGER.debug("Добавление {} предметов превысит максимальный размер стека ({})",
                        addCount, MAX_STACK_SIZE);
                return false;
            }

            // Обновляем существующую запись
            storedFoods.remove(existingEntry);
            storedFoods.add(existingEntry.withCount(existingEntry.count() + addCount));
        } else {
            // Проверяем лимит типов еды для данного tier'а
            ContainerTier tier = foodContainer.getTier();
            if (storedFoods.size() >= tier.getMaxFoodTypes()) {
                LOGGER.debug("Достигнут максимальный лимит типов еды ({}) для уровня {}",
                        tier.getMaxFoodTypes(), tier.getName());
                return false;
            }

            // Создаем новую запись
            StoredFoodEntry newEntry = new StoredFoodEntry(itemId, creationDay, addCount);
            storedFoods.add(newEntry);
        }

        // Сохраняем обновленный список
        setStoredFoods(container, storedFoods);

        LOGGER.debug("Добавлено {} предметов {} (день {}) в контейнер",
                addCount, itemId, creationDay);
        return true;
    }

    /**
     * Извлекает самую старую еду из контейнера (FIFO принцип)
     *
     * @param container ItemStack контейнера (не может быть null)
     * @param count Максимальное количество для извлечения (по умолчанию 1)
     * @return ItemStack извлеченной еды или EMPTY если контейнер пуст
     */
    public static ItemStack extractOldestFood(ItemStack container, int count) {
        // Проверки безопасности на null
        if (container == null) {
            LOGGER.error("Контейнер не может быть null");
            return ItemStack.EMPTY;
        }

        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer)) {
            LOGGER.warn("Попытка извлечь еду из неправильного контейнера");
            return ItemStack.EMPTY;
        }

        if (count <= 0) {
            LOGGER.warn("Некорректное количество для извлечения: {}", count);
            return ItemStack.EMPTY;
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(container);
        if (storedFoods.isEmpty()) {
            LOGGER.debug("Контейнер пуст, нечего извлекать");
            return ItemStack.EMPTY;
        }

        // Находим самую старую еду
        StoredFoodEntry oldestEntry = storedFoods.stream()
                .min(Comparator.comparingLong(StoredFoodEntry::creationDay))
                .orElse(null);

        if (oldestEntry == null) {
            return ItemStack.EMPTY;
        }

        // Определяем количество для извлечения
        int extractCount = Math.min(count, oldestEntry.count());

        // Создаем ItemStack для извлечения
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(oldestEntry.itemId()));
        if (item == null) {
            LOGGER.error("Не удалось найти предмет с ID: {}", oldestEntry.itemId());
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = new ItemStack(item, extractCount);
        SpoilageUtils.setCreationDay(resultStack, oldestEntry.creationDay());

        // Обновляем или удаляем запись
        storedFoods.remove(oldestEntry);
        if (oldestEntry.count() > extractCount) {
            storedFoods.add(oldestEntry.withCount(oldestEntry.count() - extractCount));
        }

        // Сохраняем обновленный список
        setStoredFoods(container, storedFoods);

        LOGGER.debug("Извлечено {} предметов {} (день {}) из контейнера",
                extractCount, oldestEntry.itemId(), oldestEntry.creationDay());
        return resultStack;
    }

    /**
     * Извлекает самую старую еду из контейнера (1 предмет)
     *
     * @param container ItemStack контейнера
     * @return ItemStack извлеченной еды или EMPTY если контейнер пуст
     */
    public static ItemStack extractOldestFood(ItemStack container) {
        return extractOldestFood(container, 1);
    }

    /**
     * Удаляет еду из контейнера
     *
     * @param container ItemStack контейнера
     * @param itemId ID предмета для удаления
     * @param count Количество для удаления
     * @return ItemStack удаленной еды или EMPTY если удаление невозможно
     */
    public static ItemStack removeFood(ItemStack container, String itemId, int count) {
        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer)) {
            LOGGER.warn("Попытка удалить еду из неправильного контейнера");
            return ItemStack.EMPTY;
        }

        if (count <= 0) {
            LOGGER.warn("Некорректное количество для удаления: {}", count);
            return ItemStack.EMPTY;
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(container);

        // Ищем запись с наиболее старой едой данного типа
        StoredFoodEntry oldestEntry = null;
        for (StoredFoodEntry entry : storedFoods) {
            if (entry.itemId().equals(itemId)) {
                if (oldestEntry == null || entry.creationDay() < oldestEntry.creationDay()) {
                    oldestEntry = entry;
                }
            }
        }

        if (oldestEntry == null) {
            LOGGER.debug("Еда типа {} не найдена в контейнере", itemId);
            return ItemStack.EMPTY;
        }

        // Проверяем, достаточно ли предметов
        int availableCount = oldestEntry.count();
        int removeCount = Math.min(count, availableCount);

        // Создаем ItemStack для извлечения
        Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            LOGGER.error("Не удалось найти предмет с ID: {}", itemId);
            return ItemStack.EMPTY;
        }

        ItemStack resultStack = new ItemStack(item, removeCount);
        SpoilageUtils.setCreationDay(resultStack, oldestEntry.creationDay());

        // Обновляем или удаляем запись
        storedFoods.remove(oldestEntry);
        if (availableCount > removeCount) {
            storedFoods.add(oldestEntry.withCount(availableCount - removeCount));
        }

        // Сохраняем обновленный список
        setStoredFoods(container, storedFoods);

        LOGGER.debug("Удалено {} предметов {} (день {}) из контейнера",
                removeCount, itemId, oldestEntry.creationDay());
        return resultStack;
    }

    /**
     * Получает список сохраненной еды из контейнера
     *
     * @param container ItemStack контейнера
     * @return Список записей о сохраненной еде
     */
    public static List<StoredFoodEntry> getStoredFoods(ItemStack container) {
        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer)) {
            return new ArrayList<>();
        }

        List<StoredFoodEntry> storedFoods = container.get(MetaphysicsSpoilage.STORED_FOOD_LIST.get());
        return storedFoods != null ? new ArrayList<>(storedFoods) : new ArrayList<>();
    }

    /**
     * Сохраняет список еды в контейнер
     *
     * @param container ItemStack контейнера
     * @param storedFoods Список записей для сохранения
     */
    private static void setStoredFoods(ItemStack container, List<StoredFoodEntry> storedFoods) {
        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer)) {
            return;
        }

        if (storedFoods.isEmpty()) {
            container.remove(MetaphysicsSpoilage.STORED_FOOD_LIST.get());
        } else {
            container.set(MetaphysicsSpoilage.STORED_FOOD_LIST.get(), new ArrayList<>(storedFoods));
        }
    }

    /**
     * Получает общее количество предметов в контейнере
     *
     * @param container ItemStack контейнера
     * @return Общее количество предметов
     */
    public static int getTotalCount(ItemStack container) {
        List<StoredFoodEntry> storedFoods = getStoredFoods(container);
        return storedFoods.stream().mapToInt(StoredFoodEntry::count).sum();
    }

    /**
     * Проверяет, пуст ли контейнер
     *
     * @param container ItemStack контейнера
     * @return true если контейнер пуст
     */
    public static boolean isEmpty(ItemStack container) {
        return getStoredFoods(container).isEmpty();
    }

    /**
     * Употребляет еду из контейнера
     *
     * @param container ItemStack контейнера
     * @param player Игрок, который употребляет еду
     * @return true если еда была успешно употреблена
     */
    public static boolean consumeFood(ItemStack container, Player player) {
        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer) || player == null) {
            return false;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return false; // Выполняем только на серверной стороне
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(container);
        if (storedFoods.isEmpty()) {
            LOGGER.debug("Контейнер пуст, нечего употреблять");
            return false;
        }

        // Проверяем порчу перед употреблением
        checkAndRemoveSpoiledFood(container, (ServerLevel) level);
        storedFoods = getStoredFoods(container); // Обновляем список после проверки порчи

        if (storedFoods.isEmpty()) {
            LOGGER.debug("Вся еда в контейнере испорчена");
            return false;
        }

        // Находим наиболее свежую еду для употребления
        StoredFoodEntry freshestEntry = storedFoods.stream()
                .max(Comparator.comparingLong(StoredFoodEntry::creationDay))
                .orElse(null);

        if (freshestEntry == null) {
            return false;
        }

        // Получаем предмет еды
        Item foodItem = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(freshestEntry.itemId()));
        if (foodItem == null) {
            LOGGER.error("Не удалось найти предмет еды: {}", freshestEntry.itemId());
            return false;
        }

        // Проверяем, является ли предмет едой
        ItemStack foodStack = new ItemStack(foodItem);
        FoodProperties foodProperties = foodStack.get(DataComponents.FOOD);
        if (foodProperties == null) {
            LOGGER.warn("Предмет {} не является едой", freshestEntry.itemId());
            return false;
        }

        // Проверяем, может ли игрок есть (голоден ли он или еда всегда съедобна)
        if (!player.canEat(foodProperties.canAlwaysEat())) {
            LOGGER.debug("Игрок {} не может есть сейчас", player.getName().getString());
            return false;
        }

        // Употребляем еду
        player.getFoodData().eat(foodProperties.nutrition(), foodProperties.saturation());

        // Применяем эффекты еды (если есть)
        // В NeoForge 1.21.8 FoodProperties.effects() может не существовать или быть другим
        // Пропускаем применение эффектов пока что

        // Удаляем один предмет из контейнера
        storedFoods.remove(freshestEntry);
        if (freshestEntry.count() > 1) {
            storedFoods.add(freshestEntry.withCount(freshestEntry.count() - 1));
        }
        setStoredFoods(container, storedFoods);

        // Воспроизводим звук поедания
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);

        LOGGER.debug("Игрок {} употребил {} из контейнера",
                player.getName().getString(), freshestEntry.itemId());
        return true;
    }

    /**
     * Проверяет и удаляет испорченную еду из контейнера
     *
     * @param container ItemStack контейнера
     * @param level Серверный уровень
     */
    public static void checkAndRemoveSpoiledFood(ItemStack container, ServerLevel level) {
        if (container.isEmpty() || !(container.getItem() instanceof FoodContainer)) {
            return;
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(container);
        List<StoredFoodEntry> freshFoods = new ArrayList<>();
        int removedCount = 0;
        int transformedCount = 0;

        LOGGER.info("=== НАЧАЛО ПРОВЕРКИ ПОРЧИ В КОНТЕЙНЕРЕ ===");
        LOGGER.info("Контейнер содержит {} записей еды", storedFoods.size());

        for (StoredFoodEntry entry : storedFoods) {
            LOGGER.info("Проверяем запись: {} x{} (день создания: {})",
                    entry.itemId(), entry.count(), entry.creationDay());

            // Создаем временный ItemStack для проверки порчи
            ResourceLocation itemLocation = ResourceLocation.parse(entry.itemId());
            // Получаем предмет из регистра (работает как для ванильных, так и для модифицированных предметов)
            Item item = BuiltInRegistries.ITEM.getValue(itemLocation);
            if (item != null) {
                ItemStack tempStack = new ItemStack(item, entry.count());
                SpoilageUtils.setCreationDay(tempStack, entry.creationDay());

                // Проверяем настройки системы порчи
                boolean systemEnabled = Config.ENABLE_SPOILAGE_SYSTEM.get();
                Config.SpoilageMode mode = Config.SPOILAGE_MODE.get();
                LOGGER.info("Настройки системы порчи: включена={}, режим={}", systemEnabled, mode.getName());

                // Проверяем, испорчена ли еда
                boolean isSpoiled = SpoilageChecker.isItemSpoiled(tempStack, level);
                long currentDay = com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker.getInstance(level).getCurrentDay();
                long daysDiff = currentDay - entry.creationDay();

                LOGGER.info("Проверка порчи: {} (день {}, текущий день {}, прошло дней: {}) -> {}",
                        entry.itemId(), entry.creationDay(), currentDay, daysDiff,
                        isSpoiled ? "ИСПОРЧЕНА" : "СВЕЖАЯ");

                if (!isSpoiled) {
                    freshFoods.add(entry);
                } else {
                    // Обрабатываем испорченную еду согласно настройкам
                    LOGGER.info("ЕДА ИСПОРЧЕНА! Начинаем обработку...");

                    if (mode == Config.SpoilageMode.TRANSFORM_TO_SPOILED) {
                        LOGGER.info("Режим превращения активен. Проверяем возможность превращения для {}", entry.itemId());
                        boolean canTransform = SpoilageTransformer.canItemBeTransformed(item);
                        LOGGER.info("Можно ли превратить {}: {}", entry.itemId(), canTransform);

                        if (canTransform) {
                            LOGGER.info("Пытаемся превратить {} через SpoilageTransformer", entry.itemId());
                            ItemStack transformedStack = SpoilageTransformer.transformSpoiledItem(tempStack, item);

                            if (!transformedStack.isEmpty()) {
                                // Добавляем превращенную еду обратно в контейнер
                                // Превращенная еда получает текущий день как день создания (она "свежая" после превращения)
                                String transformedItemId = BuiltInRegistries.ITEM.getKey(transformedStack.getItem()).toString();
                                StoredFoodEntry transformedEntry = new StoredFoodEntry(
                                    transformedItemId,
                                    currentDay,
                                    transformedStack.getCount()
                                );
                                freshFoods.add(transformedEntry);
                                transformedCount++;

                                LOGGER.info("Превращена испорченная еда в контейнере: {} x{} -> {} x{} (новый день создания: {})",
                                        entry.itemId(), entry.count(),
                                        transformedItemId, transformedStack.getCount(),
                                        currentDay);
                            } else {
                                // Если превращение не удалось, используем fallback
                                Item spoiledItem = TimedFoodManager.getSpoiledType(item).getSpoiledItem();
                                if (spoiledItem != Items.AIR) {
                                    String spoiledItemId = BuiltInRegistries.ITEM.getKey(spoiledItem).toString();
                                    StoredFoodEntry spoiledEntry = new StoredFoodEntry(
                                        spoiledItemId,
                                        currentDay,
                                        entry.count()
                                    );
                                    freshFoods.add(spoiledEntry);
                                    transformedCount++;

                                    LOGGER.info("Превращена испорченная еда в контейнере (fallback): {} x{} -> {} x{} (новый день создания: {})",
                                            entry.itemId(), entry.count(),
                                            spoiledItemId, entry.count(),
                                            currentDay);
                                } else {
                                    // Если и fallback не сработал, удаляем
                                    removedCount += entry.count();
                                    LOGGER.info("Удалена испорченная еда (не удалось превратить): {} x{} (день {})",
                                            entry.itemId(), entry.count(), entry.creationDay());
                                }
                            }
                        } else {
                            // Если превращение невозможно
                            removedCount += entry.count();
                            LOGGER.info("Удалена испорченная еда (превращение невозможно): {} x{} (день {})",
                                    entry.itemId(), entry.count(), entry.creationDay());
                        }
                    } else {
                        // Режим мгновенного исчезновения
                        removedCount += entry.count();
                        LOGGER.info("Удалена испорченная еда (режим {}): {} x{} (день {})",
                                mode.getName(), entry.itemId(), entry.count(), entry.creationDay());
                    }
                }
            }
        }

        // Обновляем контейнер если что-то изменилось
        if (removedCount > 0 || transformedCount > 0 || freshFoods.size() != storedFoods.size()) {
            setStoredFoods(container, freshFoods);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Проверяем, зажат ли Shift для открытия GUI
            if (player.isShiftKeyDown()) {
                openContainerGUI(player, stack, hand);
                return InteractionResult.SUCCESS;
            }

            // Проверяем порчу при использовании
            checkAndRemoveSpoiledFood(stack, serverLevel);

            // Пытаемся употребить еду
            if (consumeFood(stack, player)) {
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Открывает GUI контейнера для игрока
     *
     * @param player Игрок
     * @param containerStack ItemStack контейнера
     * @param hand Рука, в которой находится контейнер
     */
    private void openContainerGUI(Player player, ItemStack containerStack, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Находим слот контейнера в инвентаре игрока
            int containerSlot = -1;
            Inventory inventory = player.getInventory();

            // Упрощенная реализация для совместимости
            if (hand == InteractionHand.MAIN_HAND) {
                // Ищем слот в хотбаре, который содержит контейнер
                for (int i = 0; i < 9; i++) {
                    if (inventory.getItem(i) == containerStack) {
                        containerSlot = i;
                        break;
                    }
                }
            } else if (hand == InteractionHand.OFF_HAND) {
                containerSlot = 40; // Слот для второй руки
            }

            final int finalContainerSlot = containerSlot;

            // Создаем MenuProvider для открытия GUI
            MenuProvider menuProvider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.metaphysicsspoilage.food_container");
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
                    return new FoodContainerMenu(windowId, playerInventory, containerStack, finalContainerSlot);
                }
            };

            // Открываем GUI для игрока (упрощенная реализация)
            serverPlayer.openMenu(menuProvider);

            LOGGER.debug("Открыт GUI FoodContainer для игрока {} (слот: {})",
                        player.getName().getString(), finalContainerSlot);
        }
    }

    // TODO: Исправить сигнатуру appendHoverText для NeoForge 1.21.8
    public void appendHoverTextCustom(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        Level level = context.level();

        // Проверяем порчу перед отображением tooltip
        if (level instanceof ServerLevel serverLevel) {
            checkAndRemoveSpoiledFood(stack, serverLevel);
        }

        List<StoredFoodEntry> storedFoods = getStoredFoods(stack);

        if (storedFoods.isEmpty()) {
            tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.empty_text").withStyle(ChatFormatting.GRAY));
            return;
        }

        int totalCount = getTotalCount(stack);
        tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.total_items", totalCount).withStyle(ChatFormatting.YELLOW));

        if (level instanceof ServerLevel serverLevel) {
            WorldDayTracker tracker = WorldDayTracker.getInstance(serverLevel);
            long currentDay = tracker.getCurrentDay();

            // Группируем по типам еды и показываем информацию
            Map<String, List<StoredFoodEntry>> groupedFoods = new HashMap<>();
            for (StoredFoodEntry entry : storedFoods) {
                groupedFoods.computeIfAbsent(entry.itemId(), k -> new ArrayList<>()).add(entry);
            }

            int displayCount = 0;
            for (Map.Entry<String, List<StoredFoodEntry>> group : groupedFoods.entrySet()) {
                if (displayCount >= 5) { // Ограничиваем количество строк в тултипе
                    tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.types_more", (groupedFoods.size() - displayCount))
                            .withStyle(ChatFormatting.GRAY));
                    break;
                }

                String itemId = group.getKey();
                List<StoredFoodEntry> entries = group.getValue();

                // Находим самую свежую и самую старую еду этого типа
                long minDay = entries.stream().mapToLong(StoredFoodEntry::creationDay).min().orElse(currentDay);
                long maxDay = entries.stream().mapToLong(StoredFoodEntry::creationDay).max().orElse(currentDay);
                int typeCount = entries.stream().mapToInt(StoredFoodEntry::count).sum();

                // Определяем цвет в зависимости от свежести
                ChatFormatting color = ChatFormatting.GREEN;
                long daysUntilSpoilage = Long.MAX_VALUE;

                // Проверяем срок годности для самой старой еды этого типа
                Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(itemId));
                if (item != null) {
                    ItemStack tempStack = new ItemStack(item);
                    SpoilageUtils.setCreationDay(tempStack, minDay);
                    daysUntilSpoilage = SpoilageChecker.getTimeUntilSpoilage(tempStack, serverLevel);

                    if (daysUntilSpoilage <= 0) {
                        color = ChatFormatting.RED;
                    } else if (daysUntilSpoilage <= 1) {
                        color = ChatFormatting.YELLOW;
                    }
                }

                String itemName = item != null ? item.getName(new ItemStack(item)).getString() : itemId;
                String dayInfo = (minDay == maxDay) ? "день " + minDay : "дни " + minDay + "-" + maxDay;

                if (daysUntilSpoilage == Long.MAX_VALUE) {
                    tooltipComponents.add(Component.literal("• " + itemName + " x" + typeCount + " (" + dayInfo + ")")
                            .withStyle(color));
                } else if (daysUntilSpoilage <= 0) {
                    tooltipComponents.add(Component.literal("• " + itemName + " x" + typeCount + " (" + Component.translatable("gui.metaphysicsspoilage.food_container.spoiled_text").getString() + ")")
                            .withStyle(color));
                } else {
                    tooltipComponents.add(Component.literal("• " + itemName + " x" + typeCount +
                            " (до порчи: " + daysUntilSpoilage + " дней)")
                            .withStyle(color));
                }

                displayCount++;
            }
        } else {
            // На клиенте показываем только базовую информацию
            tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.contains_types", storedFoods.size())
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.right_click_eat").withStyle(ChatFormatting.BLUE));
        tooltipComponents.add(Component.translatable("gui.metaphysicsspoilage.food_container.right_click_extract").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Контейнер светится, если содержит еду
        return !isEmpty(stack);
    }
}
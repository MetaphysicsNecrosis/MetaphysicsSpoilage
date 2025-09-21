package com.metaphysicsnecrosis.metaphysicsspoilage.events;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик событий для FoodContainer.
 * Обрабатывает взаимодействие правого клика для добавления еды в контейнер.
 */
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID)
public class FoodContainerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerHandler.class);

    /**
     * Обрабатывает правый клик игрока с едой в руке на контейнер еды.
     * Если у игрока в одной руке контейнер, а в другой еда с временной меткой,
     * то еда добавляется в контейнер.
     * Если у игрока контейнер в одной руке и пустая другая рука, то извлекается еда.
     */
    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        // Проверки безопасности
        if (player == null) {
            LOGGER.error("Player не может быть null");
            return;
        }

        if (player.level() == null) {
            LOGGER.error("Level игрока не может быть null");
            return;
        }

        if (player.level().isClientSide) {
            return; // Выполняем только на серверной стороне
        }

        // Если игрок держит Shift и в руке FoodContainer, пропускаем обработку
        // Это позволит методу use() открыть GUI
        ItemStack heldStack = event.getItemStack();
        if (player.isShiftKeyDown() && heldStack.getItem() instanceof FoodContainer) {
            LOGGER.debug("Игрок держит Shift с FoodContainer, пропускаем обработку для открытия GUI");
            return;
        }

        ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);

        // Проверяем различные комбинации рук
        boolean success = false;

        // 1. Контейнер в основной руке, еда в off-hand -> добавление
        if (mainHandStack.getItem() instanceof FoodContainer && isValidFood(offHandStack)) {
            LOGGER.debug("Попытка добавить {} x{} в контейнер (основная -> off-hand)",
                    BuiltInRegistries.ITEM.getKey(offHandStack.getItem()), offHandStack.getCount());
            if (FoodContainer.addFood(mainHandStack, offHandStack)) {
                int addedCount = offHandStack.getCount();
                offHandStack.shrink(addedCount); // Удаляем всю еду из off-hand
                success = true;
                LOGGER.info("Успешно добавлено {} x{} в контейнер из off-hand",
                        BuiltInRegistries.ITEM.getKey(offHandStack.getItem()), addedCount);
            } else {
                LOGGER.debug("Не удалось добавить еду в контейнер (основная -> off-hand)");
            }
        }
        // 2. Еда в основной руке, контейнер в off-hand -> добавление
        else if (isValidFood(mainHandStack) && offHandStack.getItem() instanceof FoodContainer) {
            LOGGER.debug("Попытка добавить {} x{} в контейнер (off-hand -> основная)",
                    BuiltInRegistries.ITEM.getKey(mainHandStack.getItem()), mainHandStack.getCount());
            if (FoodContainer.addFood(offHandStack, mainHandStack)) {
                int addedCount = mainHandStack.getCount();
                mainHandStack.shrink(addedCount); // Удаляем всю еду из основной руки
                success = true;
                LOGGER.info("Успешно добавлено {} x{} в контейнер из основной руки",
                        BuiltInRegistries.ITEM.getKey(mainHandStack.getItem()), addedCount);
            } else {
                LOGGER.debug("Не удалось добавить еду в контейнер (off-hand -> основная)");
            }
        }
        // 3. Контейнер в основной руке, пустая off-hand -> извлечение
        else if (mainHandStack.getItem() instanceof FoodContainer && offHandStack.isEmpty()) {
            LOGGER.debug("Попытка извлечь еду из контейнера в off-hand");
            if (!FoodContainer.isEmpty(mainHandStack)) {
                ItemStack extractedFood = FoodContainer.extractOldestFood(mainHandStack);
                if (!extractedFood.isEmpty()) {
                    player.setItemInHand(InteractionHand.OFF_HAND, extractedFood);
                    success = true;
                    LOGGER.info("Успешно извлечено {} x{} из контейнера в off-hand",
                            BuiltInRegistries.ITEM.getKey(extractedFood.getItem()), extractedFood.getCount());
                } else {
                    LOGGER.debug("Не удалось извлечь еду из контейнера");
                }
            } else {
                LOGGER.debug("Контейнер пуст, нечего извлекать");
            }
        }
        // 4. Пустая основная рука, контейнер в off-hand -> извлечение
        else if (mainHandStack.isEmpty() && offHandStack.getItem() instanceof FoodContainer) {
            LOGGER.debug("Попытка извлечь еду из контейнера в основную руку");
            if (!FoodContainer.isEmpty(offHandStack)) {
                ItemStack extractedFood = FoodContainer.extractOldestFood(offHandStack);
                if (!extractedFood.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, extractedFood);
                    success = true;
                    LOGGER.info("Успешно извлечено {} x{} из контейнера в основную руку",
                            BuiltInRegistries.ITEM.getKey(extractedFood.getItem()), extractedFood.getCount());
                } else {
                    LOGGER.debug("Не удалось извлечь еду из контейнера");
                }
            } else {
                LOGGER.debug("Контейнер пуст, нечего извлекать");
            }
        }

        if (success) {
            // Отменяем обычное использование предмета
            event.setCanceled(true);
        }
    }

    /**
     * Проверяет, является ли предмет валидной едой для добавления в контейнер
     *
     * @param stack ItemStack для проверки
     * @return true если предмет можно добавить в контейнер
     */
    private static boolean isValidFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Проверяем, является ли предмет едой
        if (stack.get(net.minecraft.core.component.DataComponents.FOOD) == null) {
            return false;
        }

        // Проверяем наличие временной метки
        if (!SpoilageUtils.hasTimestamp(stack)) {
            LOGGER.debug("Еда {} не имеет временной метки, не может быть добавлена в контейнер",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()));
            return false;
        }

        return true;
    }

    /**
     * Проверяет, является ли предмет контейнером для еды
     *
     * @param stack ItemStack для проверки
     * @return true если предмет является контейнером для еды
     */
    private static boolean isFoodContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FoodContainer;
    }
}
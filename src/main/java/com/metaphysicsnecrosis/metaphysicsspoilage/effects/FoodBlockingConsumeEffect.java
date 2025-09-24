package com.metaphysicsnecrosis.metaphysicsspoilage.effects;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик событий для блокировки употребления еды без временных меток.
 *
 * Реализует систему блокировки согласно конфигурации FOOD_BLOCKING_MODE:
 * - FULL_BLOCK: Полностью блокирует употребление
 * - ZERO_NUTRITION: Позволяет употребить, но без восстановления голода
 *
 * Интегрируется с:
 * - Config.FOOD_BLOCKING_MODE - режим блокировки
 * - Config.ALWAYS_EDIBLE_ITEMS - список исключений
 * - SpoilageUtils - проверка временных меток
 * - TimedFoodManager - управление временными метками
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID)
public class FoodBlockingConsumeEffect {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodBlockingConsumeEffect.class);

    /**
     * Обрабатывает событие начала употребления предметов.
     * Проверяет и блокирует употребление еды без временных меток.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        ItemStack stack = event.getItem();

        // Выполняем только на серверной стороне
        if (level.isClientSide) {
            return;
        }

        // Проверяем, что система порчи включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        // Проверяем, является ли предмет едой
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }

        // Применяем логику блокировки еды без меток
        if (shouldBlockFoodConsumption(stack, entity)) {
            handleFoodBlocking(stack, entity, level, event);
            return;
        }

        // Проверяем порчу еды с метками (заменяет FoodConsumptionMixin)
        if (TimedFoodManager.isTimedFood(stack) && entity instanceof ServerPlayer) {
            handleSpoiledFoodConsumption(stack, (ServerPlayer) entity, (ServerLevel) level, event);
        }
    }

    /**
     * Обрабатывает завершение употребления предметов для обнуления питательности
     * при употреблении еды без временной метки в режиме ZERO_NUTRITION.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        ItemStack stack = event.getItem();

        // Выполняем только на серверной стороне
        if (level.isClientSide) {
            return;
        }

        // Проверяем, что система включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        // Проверяем, является ли предмет едой
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }

        // Обрабатываем режим ZERO_NUTRITION
        if (shouldApplyZeroNutrition(stack, entity)) {
            handleZeroNutrition(stack, entity, level);
        }
    }

    /**
     * Проверяет, нужно ли блокировать употребление еды.
     *
     * @param stack ItemStack еды
     * @param entity Сущность, которая пытается есть
     * @return true если нужно блокировать
     */
    private static boolean shouldBlockFoodConsumption(ItemStack stack, LivingEntity entity) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем, входит ли предмет в список всегда съедобных
        if (Config.isAlwaysEdible(itemId)) {
            return false;
        }

        // Проверяем исключения - если предмет не может портиться (canSpoil: false), он должен оставаться съедобным
        if (!SpoilageUtils.canItemSpoil(stack.getItem())) {
            return false;
        }

        // Проверяем наличие временной метки
        if (TimedFoodManager.isTimedFood(stack)) {
            return false;
        }

        // Проверяем режим блокировки
        Config.FoodBlockingMode blockingMode = Config.FOOD_BLOCKING_MODE.get();
        return blockingMode == Config.FoodBlockingMode.FULL_BLOCK;
    }

    /**
     * Проверяет, нужно ли применить режим нулевой питательности.
     *
     * @param stack ItemStack еды
     * @param entity Сущность, которая ест
     * @return true если нужно обнулить питательность
     */
    private static boolean shouldApplyZeroNutrition(ItemStack stack, LivingEntity entity) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем режим блокировки
        if (Config.FOOD_BLOCKING_MODE.get() != Config.FoodBlockingMode.ZERO_NUTRITION) {
            return false;
        }

        // Проверяем исключения
        if (Config.isAlwaysEdible(itemId)) {
            return false;
        }

        // Проверяем исключения - если предмет не может портиться (canSpoil: false), он должен оставаться съедобным
        if (!SpoilageUtils.canItemSpoil(stack.getItem())) {
            return false;
        }

        // Проверяем наличие временной метки
        if (TimedFoodManager.isTimedFood(stack)) {
            return false;
        }

        return true;
    }

    /**
     * Обрабатывает блокировку употребления еды.
     *
     * @param stack ItemStack еды
     * @param entity Сущность
     * @param level Уровень
     * @param event Событие для отмены
     */
    private static void handleFoodBlocking(ItemStack stack, LivingEntity entity, Level level,
                                         LivingEntityUseItemEvent.Start event) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        LOGGER.debug("Полностью заблокировано употребление еды без временной метки: {} для {}",
                itemId, entity.getName().getString());

        // Отменяем событие
        event.setCanceled(true);

        // Отправляем сообщение игроку
        if (entity instanceof ServerPlayer player) {
            Component message = Component.translatable(
                "message.metaphysicsspoilage.food_blocked_no_timestamp",
                Component.translatable("item." + itemId.replace(":", "."))
            ).withStyle(ChatFormatting.RED);

            player.displayClientMessage(message, true); // true = в action bar
        }

        // Воспроизводим звук отказа
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * Обрабатывает режим нулевой питательности.
     *
     * @param stack ItemStack еды
     * @param entity Сущность
     * @param level Уровень
     */
    private static void handleZeroNutrition(ItemStack stack, LivingEntity entity, Level level) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        LOGGER.debug("Употребление без питательности для еды без временной метки: {} для {}",
                itemId, entity.getName().getString());

        // Отправляем предупреждение игроку
        if (entity instanceof ServerPlayer player) {
            Component message = Component.translatable(
                "message.metaphysicsspoilage.food_zero_nutrition",
                Component.translatable("item." + itemId.replace(":", "."))
            ).withStyle(ChatFormatting.YELLOW);

            player.displayClientMessage(message, true); // true = в action bar

            // Обнуляем восстановленный голод и насыщение
            neutralizeNutrition(player, stack);
        }

        // Воспроизводим звук предупреждения
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5F, 0.8F);
    }

    /**
     * Нейтрализует восстановленную питательность после употребления.
     *
     * @param player Игрок
     * @param stack ItemStack еды
     */
    private static void neutralizeNutrition(ServerPlayer player, ItemStack stack) {
        try {
            FoodProperties foodProps = stack.get(DataComponents.FOOD);
            if (foodProps != null) {
                // Вычитаем восстановленные значения
                player.getFoodData().setFoodLevel(
                        Math.max(0, player.getFoodData().getFoodLevel() - foodProps.nutrition()));
                player.getFoodData().setSaturation(
                        Math.max(0, player.getFoodData().getSaturationLevel() - foodProps.saturation()));

                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                LOGGER.debug("Нейтрализована питательность: {} голода, {} насыщения для {}",
                        foodProps.nutrition(), foodProps.saturation(), itemId);
            }
        } catch (Exception e) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            LOGGER.error("Ошибка при нейтрализации питательности для {}: {}", itemId, e.getMessage());
        }
    }

    /**
     * Проверяет, должен ли предмет быть заблокирован.
     *
     * @param stack ItemStack для проверки
     * @return true если предмет должен быть заблокирован
     */
    public static boolean shouldBlockFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Проверяем, что система включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем исключения
        if (Config.isAlwaysEdible(itemId)) {
            return false;
        }

        // Проверяем наличие временной метки
        if (TimedFoodManager.isTimedFood(stack)) {
            return false;
        }

        // Проверяем политику блокировки
        Config.FoodBlockingMode blockingMode = Config.FOOD_BLOCKING_MODE.get();
        return blockingMode == Config.FoodBlockingMode.FULL_BLOCK;
    }

    /**
     * Валидирует корректность работы эффекта.
     *
     * @return true если эффект работает корректно
     */
    public static boolean validateEffect() {
        try {
            // Проверяем доступность Config
            Config.FoodBlockingMode mode = Config.FOOD_BLOCKING_MODE.get();

            // Проверяем доступность TimedFoodManager
            if (!TimedFoodManager.validateManager()) {
                LOGGER.error("TimedFoodManager недоступен для FoodBlockingConsumeEffect");
                return false;
            }

            LOGGER.info("FoodBlockingConsumeEffect валидация прошла успешно. Режим блокировки: {}", mode.getName());
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации FoodBlockingConsumeEffect", e);
            return false;
        }
    }

    /**
     * Обрабатывает употребление испорченной еды (заменяет FoodConsumptionMixin).
     *
     * @param stack ItemStack еды
     * @param player Игрок
     * @param level Серверный уровень
     * @param event Событие начала использования предмета
     */
    private static void handleSpoiledFoodConsumption(ItemStack stack, ServerPlayer player, ServerLevel level, LivingEntityUseItemEvent.Start event) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем, испорчена ли еда
        if (SpoilageUtils.isItemSpoiled(stack, level)) {
            LOGGER.debug("Блокировка употребления испорченной еды: {} игроком {}", itemId, player.getName().getString());

            // Блокируем употребление
            event.setCanceled(true);

            // ВСЕГДА сначала пытаемся превратить в испорченный вариант
            ItemStack spoiledProduct = TimedFoodManager.getSpoiledProduct(stack, level);
            if (!spoiledProduct.isEmpty() && !ItemStack.matches(stack, spoiledProduct)) {
                // Заменяем предмет в руке игрока на испорченный
                if (player.getMainHandItem() == stack) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, spoiledProduct);
                } else if (player.getOffhandItem() == stack) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, spoiledProduct);
                }

                LOGGER.info("Превращен испорченный предмет {} -> {} у игрока {}",
                        itemId, BuiltInRegistries.ITEM.getKey(spoiledProduct.getItem()), player.getName().getString());
            }

            // Отправляем сообщение игроку
            Component message = Component.translatable(
                "message.metaphysicsspoilage.food_spoiled",
                Component.translatable("item." + itemId.replace(":", "."))
            ).withStyle(ChatFormatting.RED);

            player.displayClientMessage(message, true);

            // Воспроизводим звук отвращения
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.7F, 0.5F);
        }
    }

    /**
     * Получает статистику работы эффекта.
     *
     * @return Строка со статистикой
     */
    public static String getStatistics() {
        return String.format(
            "FoodBlockingConsumeEffect - Режим: %s, Система включена: %s, Исключений: %d",
            Config.FOOD_BLOCKING_MODE.get().getName(),
            Config.ENABLE_SPOILAGE_SYSTEM.get(),
            Config.ALWAYS_EDIBLE_ITEMS.get().size()
        );
    }
}
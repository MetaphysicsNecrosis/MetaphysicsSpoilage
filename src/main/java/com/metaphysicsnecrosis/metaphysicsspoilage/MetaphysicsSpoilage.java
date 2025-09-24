package com.metaphysicsnecrosis.metaphysicsspoilage;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.TimeUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageComponent;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageTransformer;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
// import com.metaphysicsnecrosis.metaphysicsspoilage.events.SpoilageTransformationHandler; // ОТКЛЮЧЕНО
import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageHooks;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerMenu;
import com.metaphysicsnecrosis.metaphysicsspoilage.effects.FoodBlockingConsumeEffect;
import com.metaphysicsnecrosis.metaphysicsspoilage.effects.FoodBlockingUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.performance.PerformanceManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerPayload;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerPayloadHandler;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerSyncPayload;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerSyncHandler;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.JsonSpoilageConfig;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MetaphysicsSpoilage.MODID)
public class MetaphysicsSpoilage {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "metaphysicsspoilage";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "metaphysicsspoilage" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "metaphysicsspoilage" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "metaphysicsspoilage" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Create a Deferred Register to hold DataComponentTypes which will all be registered under the "metaphysicsspoilage" namespace
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    // Create a Deferred Register to hold MenuTypes which will all be registered under the "metaphysicsspoilage" namespace
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    // Register the SpoilageComponent data component
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpoilageComponent>> SPOILAGE_COMPONENT =
            DATA_COMPONENT_TYPES.register("spoilage", () -> DataComponentType.<SpoilageComponent>builder()
                    .persistent(SpoilageComponent.CODEC)
                    .networkSynchronized(SpoilageComponent.STREAM_CODEC)
                    .build());

    // Register the StoredFoodList data component for FoodContainer
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<StoredFoodEntry>>> STORED_FOOD_LIST =
            DATA_COMPONENT_TYPES.register("stored_food_list", () -> DataComponentType.<List<StoredFoodEntry>>builder()
                    .persistent(StoredFoodEntry.CODEC.listOf())
                    .networkSynchronized(ByteBufCodecs.collection(java.util.ArrayList::new, StoredFoodEntry.STREAM_CODEC))
                    .build());

    // Creates a new Block with the id "metaphysicsspoilage:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "metaphysicsspoilage:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Регистрация FoodContainer разных уровней
    public static final DeferredItem<FoodContainer> FOOD_CONTAINER_BASIC = ITEMS.registerItem("food_container_basic",
            properties -> new FoodContainer(properties, FoodContainer.ContainerTier.BASIC), new Item.Properties().stacksTo(1));
    public static final DeferredItem<FoodContainer> FOOD_CONTAINER_ADVANCED = ITEMS.registerItem("food_container_advanced",
            properties -> new FoodContainer(properties, FoodContainer.ContainerTier.ADVANCED), new Item.Properties().stacksTo(1));
    public static final DeferredItem<FoodContainer> FOOD_CONTAINER_PREMIUM = ITEMS.registerItem("food_container_premium",
            properties -> new FoodContainer(properties, FoodContainer.ContainerTier.PREMIUM), new Item.Properties().stacksTo(1));

    // Регистрация MenuType для FoodContainer
    public static final DeferredHolder<MenuType<?>, MenuType<FoodContainerMenu>> FOOD_CONTAINER_MENU =
            MENU_TYPES.register("food_container", () -> new MenuType<FoodContainerMenu>(FoodContainerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // Creates a creative tab with the id "metaphysicsspoilage:example_tab" for the mod items, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.metaphysicsspoilage")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> FOOD_CONTAINER_BASIC.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(FOOD_CONTAINER_BASIC.get()); // Add the basic food container to the tab
                output.accept(FOOD_CONTAINER_ADVANCED.get()); // Add the advanced food container to the tab
                output.accept(FOOD_CONTAINER_PREMIUM.get()); // Add the premium food container to the tab
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public MetaphysicsSpoilage(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register network payloads
        modEventBus.addListener(this::registerPayloads);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so data components get registered
        DATA_COMPONENT_TYPES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so menu types get registered
        MENU_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (MetaphysicsSpoilage) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // JSON конфигурации будут загружены при старте сервера

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));

        // Инициализация и валидация системы порчи
        LOGGER.info("MetaphysicsSpoilage: Spoilage component system initialized");
        LOGGER.info("MetaphysicsSpoilage: Spoilage data configured for food items");

        // Валидация TimedFoodManager
        if (TimedFoodManager.validateManager()) {
            LOGGER.info("MetaphysicsSpoilage: TimedFoodManager validation passed");
        } else {
            LOGGER.error("MetaphysicsSpoilage: TimedFoodManager validation failed");
        }

        // Валидация SpoilageTransformer
        if (SpoilageTransformer.validateTransformer()) {
            LOGGER.info("MetaphysicsSpoilage: SpoilageTransformer validation passed");
        } else {
            LOGGER.error("MetaphysicsSpoilage: SpoilageTransformer validation failed");
        }

        // Валидация SpoilageTransformationHandler (ОТКЛЮЧЕНА - заменена на SpoilageHooks)
        // if (SpoilageTransformationHandler.validateHandler()) {
        //     LOGGER.info("MetaphysicsSpoilage: SpoilageTransformationHandler validation passed");
        // } else {
        //     LOGGER.error("MetaphysicsSpoilage: SpoilageTransformationHandler validation failed");
        // }

        // Валидация FoodBlockingConsumeEffect
        if (FoodBlockingConsumeEffect.validateEffect()) {
            LOGGER.info("MetaphysicsSpoilage: FoodBlockingConsumeEffect validation passed");
        } else {
            LOGGER.error("MetaphysicsSpoilage: FoodBlockingConsumeEffect validation failed");
        }

        // Валидация FoodBlockingUtils
        if (FoodBlockingUtils.validateUtils()) {
            LOGGER.info("MetaphysicsSpoilage: FoodBlockingUtils validation passed");
        } else {
            LOGGER.error("MetaphysicsSpoilage: FoodBlockingUtils validation failed");
        }

        // Валидация SpoilageHooks (новая упрощенная система)
        if (SpoilageHooks.validate()) {
            LOGGER.info("MetaphysicsSpoilage: SpoilageHooks validation passed");
        } else {
            LOGGER.error("MetaphysicsSpoilage: SpoilageHooks validation failed");
        }

        LOGGER.info("MetaphysicsSpoilage: SpoilageChecker system ready for use");
        LOGGER.info("MetaphysicsSpoilage: System configured with spoilage mode: {}", Config.SPOILAGE_MODE.get().getName());
        LOGGER.info("MetaphysicsSpoilage: Food blocking mode: {}", Config.FOOD_BLOCKING_MODE.get().getName());

        // Инициализация системы производительности
        if (Config.ENABLE_PERFORMANCE_PROFILING.get()) {
            LOGGER.info("MetaphysicsSpoilage: Performance profiling enabled");
        }

        if (Config.ENABLE_CACHE_OPTIMIZATION.get()) {
            LOGGER.info("MetaphysicsSpoilage: Cache optimization enabled with limit: {}", Config.CACHE_SIZE_LIMIT.get());
        }

        // Прогреваем кэши для улучшения производительности
        TimedFoodManager.warmupCaches();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Регистрируем пакет для команд FoodContainer (клиент -> сервер)
        registrar.playToServer(
            FoodContainerPayload.TYPE,
            FoodContainerPayload.STREAM_CODEC,
            FoodContainerPayloadHandler::handleOnMain
        );

        // Регистрируем пакет для синхронизации FoodContainer (сервер -> клиент)
        registrar.playToClient(
            FoodContainerSyncPayload.TYPE,
            FoodContainerSyncPayload.STREAM_CODEC,
            FoodContainerSyncHandler::handleOnMain
        );

        LOGGER.info("MetaphysicsSpoilage: Network payloads registered");
    }



    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // ОБРАБОТКА КРЕАТИВНЫХ ТАБОВ: Устанавливаем свежие метки для всей еды
        processCreativeTabFood(event);

        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }

        // Добавляем FoodContainer в вкладку Food And Drinks
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(FOOD_CONTAINER_BASIC);
            event.accept(FOOD_CONTAINER_ADVANCED);
            event.accept(FOOD_CONTAINER_PREMIUM);
        }
    }

    /**
     * Обрабатывает содержимое креативных табов для установки свежих меток на еду
     */
    private void processCreativeTabFood(BuildCreativeModeTabContentsEvent event) {
        try {
            // Обрабатываем только табы, где логично иметь еду с флагами
            if (event.getTabKey() != CreativeModeTabs.FOOD_AND_DRINKS &&
                event.getTabKey() != CreativeModeTabs.INGREDIENTS &&
                event.getTabKey() != CreativeModeTabs.SPAWN_EGGS) { // В spawn eggs может быть еда от мобов
                return;
            }

            LOGGER.debug("Обработка креативного таба {} для установки флагов порчи", event.getTabKey());

            // Правильный способ обработать предметы в креативном табе:
            // Используем accept() для добавления предметов с нужными флагами
            // Сначала получаем все предметы, которые могут портиться
            var foodItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(item -> {
                    net.minecraft.world.item.ItemStack testStack = new net.minecraft.world.item.ItemStack(item);
                    return testStack.has(net.minecraft.core.component.DataComponents.FOOD) &&
                           com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils.canItemSpoil(item);
                })
                .toList();

            LOGGER.debug("Найдено {} предметов еды для обработки в креативном табе", foodItems.size());

            // Для каждого такого предмета создаем ItemStack с флагом TRANSIENT_NEVER_DECAY
            for (var item : foodItems) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);

                // Устанавливаем флаг "временно не портится" для креативного режима
                com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageHooks.setTransientNonDecaying(stack);

                // Добавляем в таб через accept (это перезапишет стандартный стек)
                event.accept(stack);

                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                LOGGER.debug("Установлен TRANSIENT_NEVER_DECAY для {} в креативном табе", itemId);
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке креативного таба: {}", e.getMessage());
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
        LOGGER.info("MetaphysicsSpoilage: Day tracking system initialized");

        // Загружаем JSON конфигурации порчи
        try {
            LOGGER.info("MetaphysicsSpoilage: Loading JSON spoilage configurations...");
            JsonSpoilageConfig.loadJsonConfigs(event.getServer().getResourceManager());
            LOGGER.info("MetaphysicsSpoilage: JSON spoilage configurations loaded: {}", JsonSpoilageConfig.getStats());
        } catch (Exception e) {
            LOGGER.error("MetaphysicsSpoilage: Failed to load JSON spoilage configurations", e);
        }

        // Валидация SpoilageChecker при запуске сервера
        event.getServer().getAllLevels().forEach(level -> {
            if (SpoilageChecker.validate(level)) {
                LOGGER.info("MetaphysicsSpoilage: SpoilageChecker validation passed for dimension {}",
                        level.dimension().location());
            } else {
                LOGGER.error("MetaphysicsSpoilage: SpoilageChecker validation failed for dimension {}",
                        level.dimension().location());
            }
        });

        LOGGER.info("MetaphysicsSpoilage: Server startup validation completed");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        // Синхронизируем трекер дней с мировым временем только раз в секунду
        // чтобы избежать спама логов
        if (event.getServer().getTickCount() % 20 == 0) {
            event.getServer().getAllLevels().forEach(level -> {
                if (TimeUtils.synchronizeDayTracker(level)) {
                    WorldDayTracker tracker = WorldDayTracker.getInstance(level);
                    LOGGER.info("MetaphysicsSpoilage: Day changed to {} in dimension {}",
                        tracker.getCurrentDay(), level.dimension().location());
                }
            });
        }

        // Периодические отчеты о производительности (каждые 30 минут = 36000 тиков)
        if (Config.ENABLE_PERFORMANCE_PROFILING.get() && event.getServer().getTickCount() % 36000 == 0) {
            PerformanceManager.logPerformanceReport();
        }

        // Очистка кэшей каждые 10 минут (12000 тиков)
        if (Config.ENABLE_CACHE_OPTIMIZATION.get() && event.getServer().getTickCount() % 12000 == 0) {
            TimedFoodManager.cleanupStaleCacheEntries();
        }
    }
}

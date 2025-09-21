package com.metaphysicsnecrosis.metaphysicsspoilage;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerScreen;
import com.metaphysicsnecrosis.metaphysicsspoilage.tooltip.SpoilageTooltipHandler;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MetaphysicsSpoilage.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID, value = Dist.CLIENT)
public class MetaphysicsSpoilageClient {
    public MetaphysicsSpoilageClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        MetaphysicsSpoilage.LOGGER.info("HELLO FROM CLIENT SETUP");
        MetaphysicsSpoilage.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        // Регистрация свойств модели для FoodContainer (если нужны динамические текстуры)
        event.enqueueWork(() -> {
            // Можно добавить кастомные свойства модели здесь
            // Например, для отображения разных текстур в зависимости от заполненности
            /*
            ItemProperties.register(MetaphysicsSpoilage.FOOD_CONTAINER.get(),
                ResourceLocation.fromNamespaceAndPath(MetaphysicsSpoilage.MODID, "filled"),
                (stack, world, entity, seed) -> {
                    // Возвращаем 1.0 если контейнер заполнен, 0.0 если пуст
                    List<StoredFoodEntry> foods = FoodContainer.getStoredFoods(stack);
                    return foods.isEmpty() ? 0.0f : 1.0f;
                });
            */
        });

        // Инициализация системы тултипов
        MetaphysicsSpoilage.LOGGER.info("MetaphysicsSpoilage: Tooltip system initialized");
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MetaphysicsSpoilage.FOOD_CONTAINER_MENU.get(), FoodContainerScreen::new);
        MetaphysicsSpoilage.LOGGER.info("Зарегистрирован экран FoodContainerScreen");
    }

}

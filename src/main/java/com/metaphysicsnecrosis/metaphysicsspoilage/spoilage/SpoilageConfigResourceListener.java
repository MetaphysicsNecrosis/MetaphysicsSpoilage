package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource Listener для загрузки JSON конфигураций порчи
 */
public class SpoilageConfigResourceListener extends SimplePreparableReloadListener<Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageConfigResourceListener.class);

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        // В этом методе можно выполнить предварительную подготовку данных
        // Пока что возвращаем null, так как вся логика в apply
        return null;
    }

    @Override
    protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.startTick();

        try {
            LOGGER.info("Загрузка JSON конфигураций порчи...");
            JsonSpoilageConfig.loadJsonConfigs(resourceManager);
            LOGGER.info("JSON конфигурации порчи загружены: {}", JsonSpoilageConfig.getStats());
        } catch (Exception e) {
            LOGGER.error("Ошибка при загрузке JSON конфигураций порчи", e);
        } finally {
            profiler.endTick();
        }
    }
}
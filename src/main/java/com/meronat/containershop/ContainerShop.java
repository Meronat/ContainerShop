package com.meronat.containershop;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.meronat.containershop.bstats.Metrics;
import com.meronat.containershop.configuration.Config;
import com.meronat.containershop.configuration.ConfigManager;
import com.meronat.containershop.database.Storage;
import com.meronat.containershop.entities.ShopSign;
import com.meronat.containershop.entities.ShopSignCollection;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.util.Tristate;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "containershop",
        name = "ContainerShop",
        version = "0.0.1",
        description = "A plugin which allows you to create shops with your containers.",
        url = "http://ichorcommunity.com/",
        authors = {
                "Meronat"
        }
)
public class ContainerShop {

    private static Storage storage;

    private static Logger logger;
    private static Path folder;

    private static EconomyService economyService;

    private static ConfigManager<Config> config;

    private static ShopSignCollection signCollection = new ShopSignCollection();

    private static Set<UUID> bypassing = new HashSet<>();

    @Inject
    private Metrics metrics;

    private ConfigurationLoader<CommentedConfigurationNode> loader;

    private boolean error;

    @Inject
    public ContainerShop(Logger shopLogger, @DefaultConfig(sharedRoot = false) Path path,
                         @DefaultConfig(sharedRoot = false) ConfigurationLoader<CommentedConfigurationNode> loader) {

        ContainerShop.logger = shopLogger;
        ContainerShop.folder = path.getParent();

        this.loader = loader;

    }

    @Listener
    public void onPreInitialization(GamePreInitializationEvent event) {

        try {

            storage = new Storage();

        } catch (SQLException e) {

            e.printStackTrace();
            logger.error("There was a problem setting up ContainerShop's storage.");

            error = true;

        }

    }

    @Listener
    public void onGameInitialization(GameInitializationEvent event) {

        if (error) return;

        try {

            config = new ConfigManager<>(TypeToken.of(Config.class), loader, Config::new);

        } catch (IOException | ObjectMappingException e) {

            e.printStackTrace();
            logger.error("Could not create and/or load the config. ContainerShop will not load.");

            error = true;

            return;

        }

        Optional<EconomyService> optionalEconomyService = Sponge.getServiceManager().provide(EconomyService.class);

        if (optionalEconomyService.isPresent()) {

            economyService = optionalEconomyService.get();

        } else {

            logger.error("You need an economy plugin for ContainerShop to work!");

            error = true;

        }

    }

    @Listener
    public void onGamePostInitialization(GamePostInitializationEvent event) {

        if (error) return;

        Sponge.getServiceManager().provide(PermissionService.class).ifPresent(
                p -> p.getUserSubjects().getDefaults().getSubjectData()
                        .setPermission(p.getDefaults().getActiveContexts(), "guilds.normal", Tristate.TRUE));

    }

    @Listener
    public void onGameStarted(GameStartedServerEvent event) {

        Sponge.getScheduler().createTaskBuilder()
                .async()
                .name("signrecycler")
                .interval(10, TimeUnit.SECONDS)
                .execute(() -> {

                    ShopSignCollection shopSignCollection = ContainerShop.getSignCollection();

                    for (Map.Entry<Vector3i, ShopSign> entry : shopSignCollection.entrySet()) {

                        if (System.currentTimeMillis() - entry.getValue().getLastAccessed() >= 45000) {

                            ContainerShop.getStorage().updateSign(entry.getKey(), entry.getValue());

                            shopSignCollection.remove(entry.getKey());

                        }

                    }

                }).submit(this);

    }

    public static EconomyService getEconomyService() {

        return economyService;

    }

    public static Path getFolder() {

        return folder;

    }

    public static Logger getLogger() {

        return logger;

    }

    public static Storage getStorage() {

        return storage;

    }

    public static ShopSignCollection getSignCollection() {

        return signCollection;

    }

    public static void removeBypassing(UUID uuid) {

        bypassing.remove(uuid);

    }

    public static void addBypassing(UUID uuid) {

        bypassing.add(uuid);

    }

    public static boolean isBypassing(UUID uuid) {

        return bypassing.contains(uuid);

    }

}
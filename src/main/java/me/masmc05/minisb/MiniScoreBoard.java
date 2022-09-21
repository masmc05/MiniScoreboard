package me.masmc05.minisb;

import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.paper.PaperCommandManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import me.masmc05.minisb.v1182.VersionHelper1182;
import me.masmc05.minisb.v1192.VersionHelper1192;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class MiniScoreBoard extends JavaPlugin implements Listener {
    public static boolean papiIntegration = false;
    public static final VersionHelp versionHelp = Stream.<VersionHelp>of(
            VersionHelper1182.ISTANCE,
            VersionHelper1192.ISTANCE
    ).filter(VersionHelp::checkCompatible).findFirst().orElseThrow(() -> new IllegalStateException("This version is unsupported"));
    private final ConcurrentHashMap<UUID, PlayerProcessor> processors = new ConcurrentHashMap<>();
    private final ReentrantLock poolLock = new ReentrantLock(true);
    private final NamespacedKey toggled = new NamespacedKey(this, "toggled");
    private final AtomicLong tick = new AtomicLong();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1,
            1,
            1,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(20, true), //1 task per tick, so we'll be late updating with 1 second!
            new ThreadFactoryBuilder().setNameFormat("MiniScoreboardThread-%d").setDaemon(true).build(),
            (r, pool) -> {
                poolLock.lock();
                try {
                    int size = pool.getMaximumPoolSize();
                    pool.setMaximumPoolSize(size + 1);
                    pool.setCorePoolSize(size + 1);
                    pool.prestartAllCoreThreads();
                    pool.execute(r);
                } finally {
                    poolLock.unlock();
                }
            });
    private BukkitTask task = null;

    @Override
    public void onEnable() {
        try {
            ScoreboardConfig.loadConfig(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Couldn't load config!", e);
            Bukkit.getPluginManager().disablePlugin(this);
            pool.shutdownNow();
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(this, this::planTask, 0L, 1L);
        papiIntegration = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        Bukkit.getPluginManager().registerEvents(this, this);
        try {
            var executor = AsynchronousCommandExecutionCoordinator.<CommandSender>newBuilder()
                    .withAsynchronousParsing()
                    .withExecutor(pool)
                    .build();
            var manager = PaperCommandManager.createNative(this, executor);
            manager.registerBrigadier();
            manager.registerAsynchronousCompletions();
            var parser = new AnnotationParser<>(manager, CommandSender.class, o -> manager.createDefaultCommandMeta());
            parser.registerBuilderModifier(CommandPermission.class, (a,b) -> {
                Bukkit.getPluginManager().addPermission(new Permission(a.value()));
                return b;
            });
            parser.parse(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void planTask() {
        if (Bukkit.getOnlinePlayers().size() == 0) return;
        pool.execute(this::process);
    }

    private void process() {
        var tick = this.tick.getAndIncrement();
        for (var processor : processors.values()) processor.process(tick);
    }

    @Override
    public void onDisable() {
        pool.shutdown();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onLogin(PlayerJoinEvent event) {
        changeBoard(event.getPlayer());
    }

    public void changeBoard(Player player) {
        if (player.getPersistentDataContainer().getOrDefault(toggled, PersistentDataType.BYTE, (byte) 1) == 1) {
            Optional.ofNullable(processors.remove(player.getUniqueId())).map(PlayerProcessor::board).ifPresent(PlayerBoard::clear);

            ScoreboardConfig.CONFIGS
                    .stream()
                    .filter(config -> config.contextMatch(player))
                    .findFirst()
                    .ifPresent(config -> processors.put(player.getUniqueId(), new PlayerProcessor(player, config)));
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (processors.remove(event.getPlayer().getUniqueId()) == null) return;
        if (pool.getMaximumPoolSize() == 1 || pool.getQueue().size() > 2) return;
        pool.execute(() -> {
            poolLock.lock();
            try {
                int size = pool.getMaximumPoolSize();
                if (size == 1) return;
                pool.setCorePoolSize(size - 1);
                pool.setMaximumPoolSize(size - 1);
            } finally {
                poolLock.unlock();
            }
        });
    }

    @CommandMethod(value = "sb|scoreboard toggle", requiredSender = Player.class)
    @CommandPermission("minisb.toggle")
    public void onToggle(Player player) {
        byte old = player.getPersistentDataContainer().getOrDefault(toggled, PersistentDataType.BYTE, (byte) 1);
        byte newVal = (byte) (old == 0 ? 1 : 0);
        player.getPersistentDataContainer().set(toggled, PersistentDataType.BYTE, newVal);
        if (newVal == 1) changeBoard(player);
        else processors.remove(player.getUniqueId()).board().clear();
    }
    @CommandMethod("sb|scoreboard reload")
    @CommandPermission("minisb.reload")
    public void reload(CommandSender sender) {
        ScoreboardConfig.loadConfig(this);
        Bukkit.getOnlinePlayers().forEach(this::changeBoard);
        sender.sendMessage(Component.text("Successfully reloaded the config!", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        changeBoard(event.getPlayer());
    }

    @EventHandler
    public void onGamemode(PlayerGameModeChangeEvent event) {
        switch (event.getCause()) {
            case PLUGIN, COMMAND -> Bukkit.getScheduler().runTask(this, () -> changeBoard(event.getPlayer()));
        }
    }
}

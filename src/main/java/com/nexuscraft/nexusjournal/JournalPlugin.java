package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class JournalPlugin extends JavaPlugin implements Listener {

    private final JournalConfig cfg = new JournalConfig();
    private final Codex codex = new Codex();
    private JournalStore store;
    private JournalService service;
    private Tipper tipper;
    private PathReader path;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private int ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("custom-codex.yml", false);
        cfg.load(getConfig());
        loadCodex();
        store = new JournalStore(new File(getDataFolder(), "players.yml"), getLogger()::warning);
        store.load();
        tipper = new Tipper(this::showTip, cfg.tipGapSeconds * 1000L);
        path = pathReader();
        service = new JournalService(cfg, codex, store, JournalPlugin::enabledPluginNames, this::hiddenByPath, this::found,
                System::currentTimeMillis);
        JournalBook book = new JournalBook(new NamespacedKey(this, "book"));
        getServer().getPluginManager().registerEvents(new JournalListener(this, service, book, tipper), this);
        getServer().getPluginManager().registerEvents(this, this);
        JournalCommand command = new JournalCommand(service, book, this::reload);
        getCommand("journal").setExecutor(command);
        getCommand("journal").setTabCompleter(command);
        getServer().getServicesManager().register(JournalApi.class, service, this, ServicePriority.Normal);
        tickTask = getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        saveTask = getServer().getScheduler().runTaskTimer(this, store::flush, 600L, 600L);
        getLogger().info("NexusJournal enabled -- " + codex.size() + " pages.");
        for (String w : codex.warnings()) {
            getLogger().warning(w);
        }
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (store != null) {
            store.flush();
        }
    }

    /** Once a second: show queued tips; every five seconds: notice plugins that came or went. */
    private void tick() {
        tipper.pump(System.currentTimeMillis());
        ticks++;
        if (ticks % 5 == 0) {
            service.refreshInstalled();
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (service != null) {
            service.refreshInstalled();
        }
    }

    private void reload() {
        reloadConfig();
        cfg.load(getConfig());
        tipper.setGap(cfg.tipGapSeconds * 1000L);
        path = pathReader();
        loadCodex();
        service.refreshInstalled();
    }

    private PathReader pathReader() {
        return new PathReader(NamespacedKey.fromString("nexuspath:hidden"), cfg.pathSource.equals("permission"));
    }

    /** What a player hid in NexusPath (read from what NexusPath publishes; no dependency needed). */
    private boolean hiddenByPath(Player p, String plugin) {
        return cfg.respectPath && path.hidden(p, plugin);
    }

    private void found(Player p, Entry e) {
        String line = cfg.tipFormat.replace("{title}", e.title()).replace("{tip}", e.tip()).replace("{plugin}", e.plugin());
        tipper.offer(p.getUniqueId(), line, System.currentTimeMillis());
        if (cfg.tipChat) {
            p.sendMessage(ColorText.translate(cfg.prefix + cfg.msgFound.replace("{title}", e.title())));
        }
    }

    private boolean showTip(UUID id, String line) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            return false;
        }
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ColorText.translate(line)));
        return true;
    }

    private static Collection<String> enabledPluginNames() {
        List<String> names = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.isEnabled()) {
                names.add(p.getName());
            }
        }
        return names;
    }

    /** The codex in the jar, then the server's own custom-codex.yml on top. */
    private void loadCodex() {
        codex.clear();
        try (InputStream in = getResource("codex.yml")) {
            if (in != null) {
                codex.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), "codex.yml");
            }
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not read the built-in codex: " + ex.getMessage());
        }
        File custom = new File(getDataFolder(), "custom-codex.yml");
        if (custom.exists()) {
            codex.load(YamlConfiguration.loadConfiguration(custom), "custom-codex.yml");
        }
        codex.finish();
    }
}

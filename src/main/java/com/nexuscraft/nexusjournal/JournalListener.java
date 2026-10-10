package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * What NexusJournal watches. Every handler runs at MONITOR and ignores cancelled events, so a page opens only
 * for something that really happened, and only reads: it never changes what the event does.
 */
final class JournalListener implements Listener {

    private final Plugin plugin;
    private final JournalService service;
    private final JournalBook book;
    private final Tipper tipper;

    JournalListener(Plugin plugin, JournalService service, JournalBook book, Tipper tipper) {
        this.plugin = plugin;
        this.service = service;
        this.book = book;
        this.tipper = tipper;
    }

    private void saw(Player p, String kind, String value) {
        if (p != null) {
            service.happened(p, kind, value);
        }
    }

    private static String name(ItemStack item) {
        return item == null || item.getType() == null ? "" : item.getType().name();
    }

    // ------------------------------------------------------------------ things a player does

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        saw(event.getPlayer(), "break", event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        saw(event.getPlayer(), "place", event.getBlock().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player p) {
            ItemStack made = event.getCurrentItem();
            if (made == null && event.getRecipe() != null) {
                made = event.getRecipe().getResult();
            }
            saw(p, "craft", name(made));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getClickedBlock() != null) {
            saw(event.getPlayer(), "interact", event.getClickedBlock().getType().name());
        }
        if (event.getItem() != null) {
            saw(event.getPlayer(), "use", name(event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        saw(event.getPlayer(), "consume", name(event.getItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        saw(event.getPlayer(), "bucket", event.getBucket() == null ? "" : event.getBucket().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom() != null && event.getTo() != null && event.getTo().getWorld() != null
                && event.getFrom().getWorld() != event.getTo().getWorld()) {
            saw(event.getPlayer(), "world", event.getTo().getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player p) {
            saw(p, "breed", event.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead != null && dead.getKiller() != null) {
            saw(dead.getKiller(), "kill", dead.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        saw(event.getEntity(), "death", "player");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        saw(event.getEnchanter(), "enchant", name(event.getItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        saw(event.getPlayer(), "fish", event.getState().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        saw(event.getPlayer(), "sleep", event.getBedEnterResult().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && event.getView() != null && event.getView().getTopInventory() != null
                && !(event.getView().getTopInventory().getHolder() instanceof JournalGui)) {
            saw(p, "open", event.getView().getTopInventory().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = CommandOwner.label(event.getMessage());
        service.commandTyped(event.getPlayer(), label, CommandOwner.pluginOf(label));
    }

    // ------------------------------------------------------------------ the book

    @EventHandler(priority = EventPriority.HIGH)
    public void onBookUse(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (book.isBook(event.getItem())) {
            event.setCancelled(true);
            JournalGui.open(service, event.getPlayer(), 0, JournalGui.Filter.ALL);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        saw(p, "join", "join");
        if (!service.cfg().bookOnJoin || service.store().of(p.getUniqueId()).bookGiven) {
            return;
        }
        long ticks = service.cfg().bookDelaySeconds * 20L;
        if (ticks <= 0) {
            welcome(p);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> welcome(p), ticks);
    }

    /** Gives the first journal, once. If the inventory is full nothing is marked, so the next join tries again. */
    void welcome(Player p) {
        Progress pr = service.store().of(p.getUniqueId());
        if (!p.isOnline() || pr.bookGiven) {
            return;
        }
        boolean had = book.carries(p);
        if (had || book.give(p)) {
            pr.bookGiven = true;
            service.store().markDirty();
            if (!had) {
                p.sendMessage(ColorText.translate(service.cfg().prefix + service.cfg().msgBook));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tipper.forget(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ the menu

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof JournalGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p && event.getClickedInventory() == top
                && event.getRawSlot() >= 0 && event.getRawSlot() < JournalGui.SIZE) {
            gui.click(service, p, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof JournalGui) {
            event.setCancelled(true);
        }
    }
}

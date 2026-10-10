package com.nexuscraft.nexusjournal;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** Every event the journal watches, and the book. */
public final class ListenerTest {

    static Kit.MapSection kinds() {
        Kit.MapSection m = new Kit.MapSection();
        Env.page(m, "k-break", "", "Break", "BOOK", "g", "t", "b", "h", "break:DIAMOND_ORE");
        Env.page(m, "k-place", "", "Place", "BOOK", "g", "t", "b", "h", "place:RAIL");
        Env.page(m, "k-craft", "", "Craft", "BOOK", "g", "t", "b", "h", "craft:CROSSBOW");
        Env.page(m, "k-use", "", "Use", "BOOK", "g", "t", "b", "h", "use:GOLDEN_HOE");
        Env.page(m, "k-interact", "", "Interact", "BOOK", "g", "t", "b", "h", "interact:FURNACE");
        Env.page(m, "k-consume", "", "Consume", "BOOK", "g", "t", "b", "h", "consume:BREAD");
        Env.page(m, "k-bucket", "", "Bucket", "BOOK", "g", "t", "b", "h", "bucket:LAVA_BUCKET");
        Env.page(m, "k-world", "", "World", "BOOK", "g", "t", "b", "h", "world:world_nether");
        Env.page(m, "k-breed", "", "Breed", "BOOK", "g", "t", "b", "h", "breed:ZOMBIE");
        Env.page(m, "k-kill", "", "Kill", "BOOK", "g", "t", "b", "h", "kill:ZOMBIE");
        Env.page(m, "k-death", "", "Death", "BOOK", "g", "t", "b", "h", "death");
        Env.page(m, "k-enchant", "", "Enchant", "BOOK", "g", "t", "b", "h", "enchant:IRON_PICKAXE");
        Env.page(m, "k-fish", "", "Fish", "BOOK", "g", "t", "b", "h", "fish:FISHING");
        Env.page(m, "k-sleep", "", "Sleep", "BOOK", "g", "t", "b", "h", "sleep:OK");
        Env.page(m, "k-open", "", "Open", "BOOK", "g", "t", "b", "h", "open:FURNACE");
        Env.page(m, "k-join", "", "Join", "BOOK", "g", "t", "b", "h", "join");
        Env.page(m, "k-cmd", "NexusRime", "Cmd", "BOOK", "g", "t", "b", "h", "command:rime");
        return m;
    }

    static Block block(Material type, int x) {
        Block b = Kit.WORLD.getBlockAt(x, 64, 5);
        b.setType(type);
        return b;
    }

    static boolean got(Env e, Player p, String id) {
        return e.has(p, id);
    }

    public static void main(String[] args) {
        Env e = new Env();
        Codex c = new Codex();
        c.load(kinds(), "t");
        c.finish();
        JournalService svc = new JournalService(e.cfg, c, e.store, () -> (Collection<String>) e.installed,
                (p, pl) -> e.hidden.contains(p.getName() + ":" + pl.toLowerCase()), (p, en) -> e.opened.add(p.getName() + ":" + en.id()), e.clock::get);
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        JournalBook book = new JournalBook(new NamespacedKey(plugin, "book"));
        List<String> tips = new ArrayList<>();
        Tipper tipper = new Tipper((id, line) -> tips.add(line), 0);
        JournalListener l = new JournalListener(plugin, svc, book, tipper);
        Kit.TestPlayer ann = e.player("Ann");

        // ---- blocks
        l.onBreak(new BlockBreakEvent(block(Material.STONE, 1), ann));
        expect(e.opened.isEmpty(), "breaking stone opens nothing");
        l.onBreak(new BlockBreakEvent(block(Material.DIAMOND_ORE, 2), ann));
        expect(got(e, ann, "k-break") && e.opened.size() == 1, "breaking the named ore opens its page");
        l.onBreak(new BlockBreakEvent(block(Material.DIAMOND_ORE, 3), null));
        expect(e.opened.size() == 1, "a break with no player is ignored");
        l.onPlace(new BlockPlaceEvent(block(Material.STONE, 4), ann));
        expect(!got(e, ann, "k-place"), "placing the wrong block");
        l.onPlace(new BlockPlaceEvent(block(Material.RAIL, 5), ann));
        expect(got(e, ann, "k-place"), "placing a rail opens its page");

        // ---- crafting
        Inventory out = new Inventory();
        out.setItem(0, new ItemStack(Material.CROSSBOW));
        InventoryView craftView = new InventoryView();
        craftView.setTopInventory(out);
        craftView.setPlayer(ann);
        l.onCraft(new CraftItemEvent(null, craftView, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(got(e, ann, "k-craft"), "crafting the named item opens its page");
        Env e2 = new Env();
        JournalService svc2 = new JournalService(e2.cfg, c, e2.store, () -> e2.installed, (p, pl) -> false, (p, en) -> e2.opened.add(en.id()), e2.clock::get);
        JournalListener l2 = new JournalListener(plugin, svc2, book, tipper);
        Kit.TestPlayer ben = e2.player("Ben");
        Inventory other = new Inventory();
        other.setItem(0, new ItemStack(Material.STONE));
        InventoryView otherView = new InventoryView();
        otherView.setTopInventory(other);
        otherView.setPlayer(ben);
        l2.onCraft(new CraftItemEvent(null, otherView, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(e2.opened.isEmpty(), "crafting something else opens nothing");
        InventoryView noViewer = new InventoryView();
        noViewer.setTopInventory(out);
        l2.onCraft(new CraftItemEvent(null, noViewer, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(e2.opened.isEmpty(), "a craft with nobody at it is ignored");
        Inventory empty = new Inventory();
        InventoryView emptyView = new InventoryView();
        emptyView.setTopInventory(empty);
        emptyView.setPlayer(ben);
        l2.onCraft(new CraftItemEvent(null, emptyView, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(e2.opened.isEmpty(), "a craft whose result is empty and has no recipe is ignored without a crash");

        // ---- interact: items and blocks, right clicks, main hand
        l.onUse(new PlayerInteractEvent(ann, Action.LEFT_CLICK_AIR, new ItemStack(Material.GOLDEN_HOE), null));
        expect(!got(e, ann, "k-use"), "a left click does not count");
        l.onUse(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_AIR, new ItemStack(Material.STONE), null));
        expect(!got(e, ann, "k-use"), "the wrong item does not count");
        PlayerInteractEvent off = new PlayerInteractEvent(ann, Action.RIGHT_CLICK_AIR, new ItemStack(Material.GOLDEN_HOE), null) {
            @Override
            public EquipmentSlot getHand() {
                return EquipmentSlot.OFF_HAND;
            }
        };
        l.onUse(off);
        expect(!got(e, ann, "k-use"), "the off hand is the same click seen twice: ignored");
        l.onUse(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_AIR, new ItemStack(Material.GOLDEN_HOE), null));
        expect(got(e, ann, "k-use"), "right-clicking the named item opens its page");
        l.onUse(new PlayerInteractEvent(ann, Action.PHYSICAL, null, block(Material.FURNACE, 6)));
        expect(!got(e, ann, "k-interact"), "stepping on a block is not using it");
        l.onUse(new PlayerInteractEvent(ann, Action.LEFT_CLICK_BLOCK, null, block(Material.FURNACE, 6)));
        expect(!got(e, ann, "k-interact"), "punching a block is not using it");
        l.onUse(new PlayerInteractEvent(ann, Action.RIGHT_CLICK_BLOCK, null, block(Material.FURNACE, 6)));
        expect(got(e, ann, "k-interact"), "right-clicking the named block opens its page");

        // ---- the rest of what a player does
        l.onConsume(new PlayerItemConsumeEvent(ann, new ItemStack(Material.STONE), EquipmentSlot.HAND));
        expect(!got(e, ann, "k-consume"), "eating the wrong thing");
        l.onConsume(new PlayerItemConsumeEvent(ann, new ItemStack(Material.BREAD), EquipmentSlot.HAND));
        expect(got(e, ann, "k-consume"), "eating bread");
        l.onBucket(new PlayerBucketEmptyEvent(ann, block(Material.STONE, 7), Material.WATER_BUCKET));
        expect(!got(e, ann, "k-bucket"), "the wrong bucket");
        l.onBucket(new PlayerBucketEmptyEvent(ann, block(Material.STONE, 7), Material.LAVA_BUCKET));
        expect(got(e, ann, "k-bucket"), "pouring lava");
        l.onTeleport(new PlayerTeleportEvent(ann, new Location(Kit.WORLD, 0, 64, 0), new Location(Kit.WORLD, 5, 64, 5)));
        expect(!got(e, ann, "k-world"), "moving inside a world is not entering one");
        l.onTeleport(new PlayerTeleportEvent(ann, new Location(Kit.WORLD, 0, 64, 0), new Location(null, 5, 64, 5)));
        expect(!got(e, ann, "k-world"), "a destination with no world is ignored");
        l.onTeleport(new PlayerTeleportEvent(ann, null, new Location(Kit.NETHER, 5, 64, 5)));
        expect(!got(e, ann, "k-world"), "no origin is ignored");
        l.onTeleport(new PlayerTeleportEvent(ann, new Location(Kit.WORLD, 0, 64, 0), new Location(Kit.NETHER, 5, 64, 5)));
        expect(got(e, ann, "k-world"), "entering the nether opens its page");

        Zombie child = new Zombie();
        l.onBreed(new EntityBreedEvent(child, new Zombie(), new Zombie(), null, null, 1));
        expect(!got(e, ann, "k-breed"), "breeding with no player behind it is ignored");
        l.onBreed(new EntityBreedEvent(child, new Zombie(), new Zombie(), ann, null, 1));
        expect(got(e, ann, "k-breed"), "breeding opens the page for the breeder");

        Zombie unkilled = new Zombie();
        l.onKill(new EntityDeathEvent(unkilled));
        expect(!got(e, ann, "k-kill"), "a mob that died on its own opens nothing");
        Zombie slain = new Zombie() {
            @Override
            public Player getKiller() {
                return ann;
            }
        };
        l.onKill(new EntityDeathEvent(slain));
        expect(got(e, ann, "k-kill"), "killing a zombie opens the page for the killer");
        Kit.TestPlayer cat = e.player("Cat");
        l.onKill(new EntityDeathEvent(new Zombie() {
            @Override
            public EntityType getType() {
                return EntityType.SKELETON;
            }

            @Override
            public Player getKiller() {
                return cat;
            }
        }));
        expect(!got(e, cat, "k-kill"), "killing something else does not");

        l.onDeath(new PlayerDeathEvent(cat, new ArrayList<>(), 0, null));
        expect(got(e, cat, "k-death"), "dying opens the death page");

        l.onEnchant(new EnchantItemEvent(ann, null, new ItemStack(Material.STONE), new java.util.HashMap<>(), 0, 1));
        expect(!got(e, ann, "k-enchant"), "enchanting the wrong item");
        l.onEnchant(new EnchantItemEvent(ann, null, new ItemStack(Material.IRON_PICKAXE), new java.util.HashMap<>(), 0, 1));
        expect(got(e, ann, "k-enchant"), "enchanting the named item");

        l.onFish(new PlayerFishEvent(ann, null, null, PlayerFishEvent.State.CAUGHT_FISH));
        expect(!got(e, ann, "k-fish"), "a catch is not a cast");
        l.onFish(new PlayerFishEvent(ann, null, null, PlayerFishEvent.State.FISHING));
        expect(got(e, ann, "k-fish"), "casting opens the fishing page");

        l.onSleep(new PlayerBedEnterEvent(ann, block(Material.STONE, 8), PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_HERE));
        expect(!got(e, ann, "k-sleep"), "a bed that refused you");
        l.onSleep(new PlayerBedEnterEvent(ann, block(Material.STONE, 8), PlayerBedEnterEvent.BedEnterResult.OK));
        expect(got(e, ann, "k-sleep"), "sleeping opens the page");

        // ---- inventories
        Inventory furnace = new Inventory() {
            @Override
            public InventoryType getType() {
                return InventoryType.FURNACE;
            }
        };
        InventoryView fv = new InventoryView();
        fv.setTopInventory(furnace);
        fv.setPlayer(ann);
        l.onOpen(new InventoryOpenEvent(fv));
        expect(got(e, ann, "k-open"), "opening a furnace opens its page");
        Env e3 = new Env();
        JournalService svc3 = new JournalService(e3.cfg, c, e3.store, () -> e3.installed, (p, pl) -> false, (p, en) -> e3.opened.add(en.id()), e3.clock::get);
        JournalListener l3 = new JournalListener(plugin, svc3, book, tipper);
        Kit.TestPlayer dan = e3.player("Dan");
        InventoryView chestView = new InventoryView();
        chestView.setTopInventory(new Inventory());
        chestView.setPlayer(dan);
        l3.onOpen(new InventoryOpenEvent(chestView));
        expect(e3.opened.isEmpty(), "opening a chest opens nothing");
        Inventory menu = new Inventory() {
            @Override
            public InventoryType getType() {
                return InventoryType.FURNACE;
            }
        };
        Env e4 = new Env();
        JournalService svc4 = new JournalService(e4.cfg, e4.codex, e4.store, () -> e4.installed, (p, pl) -> false, (p, en) -> e4.opened.add(en.id()), e4.clock::get);
        JournalGui.open(svc4, dan, 0, JournalGui.Filter.ALL);
        InventoryView ours = new InventoryView();
        ours.setTopInventory(dan.opened);
        ours.setPlayer(dan);
        l3.onOpen(new InventoryOpenEvent(ours));
        expect(e3.opened.isEmpty(), "the journal's own menu is never a page trigger");
        InventoryView nobody = new InventoryView();
        nobody.setTopInventory(furnace);
        l3.onOpen(new InventoryOpenEvent(nobody));
        expect(e3.opened.isEmpty() && menu != null, "a view nobody looks at is ignored");

        // ---- commands
        Kit.NexusRime rimePlugin = new Kit.NexusRime();
        Env.command(rimePlugin, "rime", "winter");
        l3.onCommand(new PlayerCommandPreprocessEvent(dan, "/rime now"));
        expect(e3.opened.equals(List.of("k-cmd")), "typing the plugin's command opens its page: " + e3.opened);
        Env e5 = new Env();
        JournalService svc5 = new JournalService(e5.cfg, c, e5.store, () -> e5.installed, (p, pl) -> false, (p, en) -> e5.opened.add(en.id()), e5.clock::get);
        JournalListener l5 = new JournalListener(plugin, svc5, book, tipper);
        Kit.TestPlayer eve = e5.player("Eve");
        Env.command(rimePlugin, "rime", "winter");
        l5.onCommand(new PlayerCommandPreprocessEvent(eve, "/winter"));
        expect(e5.opened.isEmpty(), "an alias is another label: the page names /rime only");
        l5.onCommand(new PlayerCommandPreprocessEvent(eve, "/unknowncommand"));
        expect(e5.opened.isEmpty(), "a command nobody owns opens nothing");
        l5.onCommand(new PlayerCommandPreprocessEvent(eve, "/RIME"));
        expect(e5.opened.equals(List.of("k-cmd")), "labels are lower-cased");

        // ---- joining and the book
        Env j = new Env();
        JournalService sj = new JournalService(j.cfg, c, j.store, () -> j.installed, (p, pl) -> false, (p, en) -> j.opened.add(en.id()), j.clock::get);
        JournalListener lj = new JournalListener(plugin, sj, book, tipper);
        Kit.TestPlayer fay = j.player("Fay");
        j.cfg.bookDelaySeconds = 0;
        lj.onJoin(new PlayerJoinEvent(fay, ""));
        expect(sj.has(fay.getUniqueId(), "k-join"), "joining opens the join page");
        expect(book.carries(fay) && j.store.peek(fay.getUniqueId()).bookGiven, "with no delay the book arrives at once and is marked");
        expect(fay.heard("field journal"), "and she is told");
        int books = count(book, fay);
        lj.onJoin(new PlayerJoinEvent(fay, ""));
        expect(count(book, fay) == books && books == 1, "joining again gives no second book");
        j.cfg.bookDelaySeconds = 10;
        Kit.TestPlayer gil = j.player("Gil");
        BukkitScheduler.LATER.clear();
        lj.onJoin(new PlayerJoinEvent(gil, ""));
        expect(!book.carries(gil) && BukkitScheduler.LATER.size() == 1, "with a delay the book is scheduled, not given yet");
        BukkitScheduler.LATER.get(0).run();
        expect(book.carries(gil) && j.store.peek(gil.getUniqueId()).bookGiven, "and arrives when the delay is up");
        j.cfg.bookOnJoin = false;
        Kit.TestPlayer hal = j.player("Hal");
        BukkitScheduler.LATER.clear();
        lj.onJoin(new PlayerJoinEvent(hal, ""));
        expect(BukkitScheduler.LATER.isEmpty() && !book.carries(hal), "give-on-join off: no book");
        expect(sj.has(hal.getUniqueId(), "k-join"), "but the join page still opens");
        j.cfg.bookOnJoin = true;

        // welcome(): carries already, offline, full inventory
        Kit.TestPlayer ivy = j.player("Ivy");
        ivy.getInventory().addItem(book.create());
        lj.welcome(ivy);
        expect(j.store.peek(ivy.getUniqueId()).bookGiven && count(book, ivy) == 1 && !ivy.heard("field journal"),
                "someone who already has one is marked, given nothing and told nothing");
        Kit.TestPlayer jon = j.player("Jon");
        jon.online = false;
        lj.welcome(jon);
        expect(!j.store.of(jon.getUniqueId()).bookGiven && !book.carries(jon), "an offline player gets nothing");
        jon.online = true;
        for (int i = 0; i < 40; i++) {
            jon.getInventory().addItem(new ItemStack(Material.STONE, 64));
        }
        // fill the rest of the 54 stub slots
        for (int i = 0; i < 54; i++) {
            if (jon.getInventory().getItem(i) == null) {
                jon.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
            }
        }
        lj.welcome(jon);
        expect(!j.store.peek(jon.getUniqueId()).bookGiven && !book.carries(jon), "a full inventory: nothing marked, so the next join tries again");
        jon.getInventory().setItem(3, null);
        lj.welcome(jon);
        expect(j.store.peek(jon.getUniqueId()).bookGiven && book.carries(jon), "and it works once there is room");

        // ---- using the book
        Kit.TestPlayer kim = j.player("Kim");
        ItemStack mine = book.create();
        expect(book.isBook(mine) && !book.isBook(new ItemStack(Material.BOOK)) && !book.isBook(null) && !book.isBook(new ItemStack(Material.STONE)), "only the tagged book is the journal");
        PlayerInteractEvent use = new PlayerInteractEvent(kim, Action.RIGHT_CLICK_AIR, mine, null);
        l.onBookUse(use);
        expect(use.isCancelled() && kim.opened != null && kim.opened.getHolder() instanceof JournalGui, "right-clicking the book opens the menu and cancels the click");
        kim.opened = null;
        PlayerInteractEvent hit = new PlayerInteractEvent(kim, Action.LEFT_CLICK_AIR, mine, null);
        l.onBookUse(hit);
        expect(!hit.isCancelled() && kim.opened == null, "a left click does nothing");
        PlayerInteractEvent plain = new PlayerInteractEvent(kim, Action.RIGHT_CLICK_AIR, new ItemStack(Material.BOOK), null);
        l.onBookUse(plain);
        expect(!plain.isCancelled() && kim.opened == null, "an ordinary book is left alone");
        PlayerInteractEvent offBook = new PlayerInteractEvent(kim, Action.RIGHT_CLICK_AIR, mine, null) {
            @Override
            public EquipmentSlot getHand() {
                return EquipmentSlot.OFF_HAND;
            }
        };
        l.onBookUse(offBook);
        expect(!offBook.isCancelled() && kim.opened == null, "the off-hand repeat of the click is ignored");
        PlayerInteractEvent onBlock = new PlayerInteractEvent(kim, Action.RIGHT_CLICK_BLOCK, mine, block(Material.STONE, 9));
        l.onBookUse(onBlock);
        expect(onBlock.isCancelled() && kim.opened != null, "right-clicking a block with the book also opens it");

        // ---- quitting
        UUID_CHECK:
        {
            List<String> shown = new ArrayList<>();
            Tipper slow = new Tipper((id, line) -> shown.add(line), 100_000);
            JournalListener lq = new JournalListener(plugin, svc, book, slow);
            slow.offer(kim.getUniqueId(), "one", 0);
            slow.offer(kim.getUniqueId(), "two", 1);
            expect(slow.waiting(kim.getUniqueId()) == 1, "a tip is waiting");
            lq.onQuit(new PlayerQuitEvent(kim, ""));
            expect(slow.waiting(kim.getUniqueId()) == 0, "quitting drops the waiting tips");
        }

        // ---- the menu
        Env m = new Env();
        JournalListener lm = new JournalListener(plugin, m.service, book, tipper);
        Kit.TestPlayer lou = m.player("Lou");
        JournalGui.open(m.service, lou, 0, JournalGui.Filter.ALL);
        InventoryView lv = new InventoryView();
        lv.setTopInventory(lou.opened);
        lv.setPlayer(lou);
        InventoryClickEvent click = new InventoryClickEvent(lv, InventoryType.SlotType.CONTAINER, JournalGui.FILTER, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        lm.onClick(click);
        expect(click.isCancelled(), "clicks in the journal menu are cancelled");
        expect(((JournalGui) lou.opened.getHolder()).filter == JournalGui.Filter.FOUND, "and the click reaches the button");
        InventoryDragEvent drag = new InventoryDragEvent(lv);
        lm.onDrag(drag);
        expect(drag.isCancelled(), "drags are cancelled");
        InventoryView foreign = new InventoryView();
        foreign.setTopInventory(new Inventory());
        foreign.setPlayer(lou);
        InventoryClickEvent fc = new InventoryClickEvent(foreign, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        lm.onClick(fc);
        expect(!fc.isCancelled(), "other menus are left alone");
        InventoryDragEvent fd = new InventoryDragEvent(foreign);
        lm.onDrag(fd);
        expect(!fd.isCancelled(), "other drags too");
        InventoryClickEvent none = new InventoryClickEvent(new InventoryView(), InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        lm.onClick(none);
        expect(!none.isCancelled(), "a view with no top inventory is ignored");
        InventoryDragEvent nd = new InventoryDragEvent(new InventoryView());
        lm.onDrag(nd);
        expect(!nd.isCancelled(), "a drag with no top inventory is ignored");
        JournalGui.open(m.service, lou, 0, JournalGui.Filter.ALL);
        InventoryView lv2 = new InventoryView();
        lv2.setTopInventory(lou.opened);
        lv2.setPlayer(lou);
        InventoryClickEvent low = new InventoryClickEvent(lv2, InventoryType.SlotType.QUICKBAR, JournalGui.FILTER, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        lm.onClick(low);
        expect(low.isCancelled() && ((JournalGui) lou.opened.getHolder()).filter == JournalGui.Filter.ALL, "a click in the player's own inventory below the menu is cancelled but is not a button");

        // ---- every handler is read-only and ignores cancelled events (except the ones that must not)
        Set<String> exempt = Set.of("onDeath", "onJoin", "onQuit", "onBookUse", "onClick", "onDrag");
        int handlers = 0;
        for (Method method : JournalListener.class.getDeclaredMethods()) {
            EventHandler h = method.getAnnotation(EventHandler.class);
            if (h == null) {
                continue;
            }
            handlers++;
            if (exempt.contains(method.getName())) {
                continue;
            }
            expect(h.priority() == EventPriority.MONITOR && h.ignoreCancelled(), method.getName() + " is MONITOR and ignores cancelled events");
        }
        expect(handlers == 20, "twenty handlers: " + handlers);
        try {
            JournalListener.class.getDeclaredMethod("onBookUse", PlayerInteractEvent.class).getAnnotation(EventHandler.class).priority();
            expect(JournalListener.class.getDeclaredMethod("onBookUse", PlayerInteractEvent.class).getAnnotation(EventHandler.class).priority() == EventPriority.HIGH, "the book runs before MONITOR so it can cancel");
        } catch (NoSuchMethodException ex) {
            expect(false, "onBookUse exists");
        }
        Kit.finish();
    }

    static int count(JournalBook book, Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (book.isBook(s)) {
                n++;
            }
        }
        return n;
    }
}

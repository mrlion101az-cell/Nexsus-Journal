#!/usr/bin/env python3
"""Builds src/main/resources/codex.yml from NexusPath's catalog.yml (names, icons, blurbs) and the sibling plugins'
plugin.yml files (which commands exist). The hand-written part is the TABLE below: for every system, its group, the
one-line tip, the hint for the closed tile, and what a player does that opens it.

  python3 tools/gen-codex.py [--catalog PATH] [--plugins DIR]      (writes src/main/resources/codex.yml)
Needs: pip install pyyaml
"""
import glob, json, os, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
plugins_dir = os.path.join(here, "..", "..")
catalog = os.path.join(plugins_dir, "NexusPath", "src", "main", "resources", "catalog.yml")
if "--plugins" in sys.argv:
    plugins_dir = sys.argv[sys.argv.index("--plugins") + 1]
if "--catalog" in sys.argv:
    catalog = sys.argv[sys.argv.index("--catalog") + 1]

# Staff tools and plumbing: players never "meet" these, so they get no page.
SKIP = {"NexusLogs", "NexusCmdTracker", "NexusGate", "NexusMobShield", "NexusNPC", "NexusPrison", "NexusPurgatory",
        "NexusTerra", "NexusAdmin", "NexusVault", "NexusWeatherReport", "NexusScan", "NexusShackles", "NexusDenyBlocks",
        "NexusSpawnShield", "NexusSpawn"}

START, MAKE, FARM, MAGIC, GO, FIGHT, BUILD, PEOPLE, STRANGE, SERVER = (
    "Getting started", "Gather and make", "Farm and fish", "Magic and mystery", "Explore and travel",
    "Fight and survive", "Build and live", "People and places", "Strange and spooky", "Server life")
GROUPS = [START, MAKE, FARM, MAGIC, GO, FIGHT, BUILD, PEOPLE, STRANGE, SERVER]

# plugin: (group, tip, hint, [triggers]). No triggers: the plugin's own commands open it, or (it has none) only
# /journal discover does.
TABLE = {
    "NexusMenu": (START, "One menu reaches every system", "Open the server menu.", []),
    "NexusStarter": (START, "Your guide to the whole server", "Ask for the starter guide.", []),
    "NexusEndeavors": (START, "Daily goals and a login streak", "Look for the daily quest board.", []),
    "NexusAchievements": (SERVER, "Milestones pop up as you play", "Keep playing; a toast will appear.", []),
    "NexusSeasons": (SERVER, "The seasons change plants and mobs", "Play through a change of season.", []),
    "NexusSentinel": (SERVER, "Report a problem with /report", "Find out how to report a problem.", []),
    "NexusGuardian": (SERVER, "Verify your age group for safe play", "Check your age group.", []),
    "NexusResourcePacks": (SERVER, "Pick the resource pack you like", "Look for the resource pack menu.", []),
    "NexusEconomy": (SERVER, "Bank, shop and auctions", "Open a shop or the bank.", []),
    "NexusPulse": (SERVER, "A paper of what you missed", "Come back after some time away.", []),
    "NexusHeartbeat": (SERVER, "The server has a shared mood", "Ask how the world feels.", []),
    "NexusRealms": (BUILD, "Claim land and protect your builds", "Start a claim or a country.", []),
    "NexusBackpacks": (MAKE, "A backpack that upgrades as you play", "Find or craft a backpack.", []),
    "NexusEnderChest": (MAKE, "Your ender chest has 20 pages", "Open an ender chest.", ["open:ENDER_CHEST"]),
    "NexusMerchants": (PEOPLE, "Villagers remember prices and you", "Trade with a villager.", ["open:MERCHANT"]),
    "NexusCreativeSurvival": (BUILD, "Creative blocks cost coins", "Try creative mode.", []),
    "NexusHusbandry": (FARM, "Breeding now has quality and care", "Breed two animals.", ["breed:*"]),
    "NexusAlchemy": (MAKE, "Brewing order matters now", "Open a brewing stand.", ["open:BREWING"]),
    "NexusCuisine": (MAKE, "Cook well; eat it while fresh", "Eat or drink something.", ["consume:*"]),
    "NexusCrafting": (MAKE, "Crafting levels up for bonus items", "Craft something.", ["craft:*"]),
    "NexusEnchants": (MAGIC, "Boots like Lava Walker exist now", "Find enchanted boots.", []),
    "NexusMagic": (MAGIC, "Elemental staves and bows", "Craft an elemental staff.", []),
    "NexusAngler": (FARM, "Ponds run dry; bait changes luck", "Cast a fishing rod.", ["fish:FISHING"]),
    "NexusSmelting": (MAKE, "Smelting builds a skill", "Open a furnace.", ["open:FURNACE|BLAST_FURNACE|SMOKER"]),
    "NexusForge": (MAKE, "Gear wears down; repair it at an anvil", "Open an anvil or grindstone.", ["open:ANVIL|GRINDSTONE"]),
    "NexusFarming": (FARM, "A golden hoe replants and harvests", "Harvest a crop.", ["break:WHEAT|CARROTS|POTATOES|BEETROOTS|NETHER_WART|MELON|PUMPKIN", "use:GOLDEN_HOE"]),
    "NexusHats": (BUILD, "Wear any block as a hat", "Try putting a block on your head.", []),
    "NexusIndustry": (BUILD, "Machines and androids, by clearance", "Find a machine.", []),
    "NexusLode": (MAKE, "Mining is a skill: veins, prospecting", "Mine some ore.", ["break:*_ORE|ANCIENT_DEBRIS"]),
    "NexusStyle": (BUILD, "Save outfits; dye armor at a loom", "Open a loom.", ["open:LOOM"]),
    "NexusCraftersGuild": (MAKE, "Auto crafters, contracts, events", "Look for the Crafters Guild.", []),
    "NexusArcanum": (MAGIC, "A better bookshelf ring: rarer enchants", "Open an enchanting table.", ["open:ENCHANTING"]),
    "NexusTitles": (PEOPLE, "Pick a chat title, banner and color", "Look for the titles menu.", []),
    "NexusWands": (MAGIC, "Bond a wand and speak spells", "Find a wand.", []),
    "NexusMap": (GO, "A live map in your hand", "Hold a map.", ["use:FILLED_MAP|MAP"]),
    "NexusFrontier": (PEOPLE, "Bounties and trade caravans", "Look for a trading post.", []),
    "NexusIntegrity": (BUILD, "Builds need support or they fall", "Build something tall.", []),
    "NexusCombat": (FIGHT, "Chain hits without being hit", "Fight a mob.", ["kill:*"]),
    "NexusDrones": (BUILD, "Drones, turrets and security", "Look for a drone.", []),
    "NexusGrave": (FIGHT, "Your items wait in a grave", "Die once.", ["death:*"]),
    "NexusMigrations": (GO, "Huge herds roam the map", "Watch for a great herd.", []),
    "NexusGrowth": (SERVER, "You grow slowly over real years", "Check how big you are.", []),
    "NexusHatchlings": (MAGIC, "Shrink and tame a mob with a crystal", "Find a hatchling crystal.", []),
    "NexusLegends": (FIGHT, "Cryptids, bounties and relics", "Look for something one of a kind.", []),
    "NexusDungeons": (GO, "Mazes with traps and a boss", "Find a dungeon entrance.", []),
    "NexusGiants": (FIGHT, "Mobs have ages that set their size", "Meet a mob.", ["kill:*"]),
    "NexusWards": (BUILD, "A statue keeps one mob away", "Craft a ward statue.", []),
    "NexusExpeditions": (PEOPLE, "Team up for Hunt or Explore trips", "Start an expedition.", []),
    "NexusCompanions": (PEOPLE, "Adopt a pet and level it up", "Adopt a companion.", []),
    "NexusPortals": (GO, "Named Nether portals and a travel menu", "Step through a portal.", ["world:*nether*|*the_end*"]),
    "NexusRails": (GO, "Stations, routes, self-driving carts", "Lay some rail.", ["place:*rail*"]),
    "NexusHydro": (BUILD, "Water and lava have real volume", "Empty a bucket.", ["bucket:WATER_BUCKET|LAVA_BUCKET"]),
    "NexusRedstone": (BUILD, "Gates, clocks, smarter hoppers", "Place a repeater or hopper.", ["place:REPEATER|COMPARATOR|OBSERVER|HOPPER"]),
    "NexusHorror": (STRANGE, "A Sanity meter and Blood Moons", "Stay out after dark.", []),
    "NexusSomnus": (BUILD, "Sleep debt, dreams, real home beds", "Try to sleep in a bed.", ["sleep:*"]),
    "NexusMinds": (FIGHT, "Mobs have personalities and grudges", "Fight a zombie, skeleton or pillager.", ["kill:ZOMBIE|SKELETON|PILLAGER"]),
    "NexusVitals": (FIGHT, "Stamina, injury, gas and stealth", "Push yourself hard.", []),
    "NexusRime": (FIGHT, "Winter hordes and a frost boss", "Survive the winter.", []),
    "NexusRequiem": (STRANGE, "Many deaths thin the veil", "Something stirs when many die.", []),
    "NexusUndertow": (STRANGE, "Over-mine a chunk; the land fights back", "Strip one patch of land bare.", []),
    "NexusSurvival": (FIGHT, "Thirst, disease and weather matter", "Go thirsty or cold.", []),
    "NexusLegion": (FIGHT, "Recruit small animals into an army", "Find a war horn.", []),
    "NexusMomentum": (GO, "Travel to level up: run, jump, fall", "Run and jump around.", []),
    "NexusWarbeasts": (MAGIC, "Truce crystals calm hostile mobs", "Find a truce crystal.", []),
    "NexusMobCrossbow": (FIGHT, "A crossbow that fires a live mob", "Hold a crossbow.", ["use:CROSSBOW"]),
    "NexusVice": (STRANGE, "Fictional potions and drinks, real risks", "Look for a strange drink.", []),
    "NexusEvents": (STRANGE, "Meteors, invasions, storms, blessings", "Wait for something big.", []),
    "NexusBloodline": (PEOPLE, "Kids inherit traits from how you play", "Raise a child.", []),
    "NexusOddities": (STRANGE, "Harmless body quirks", "Wait; it will happen.", []),
    "NexusPareidolia": (STRANGE, "Cave whispers only you hear", "Spend time in a deep cave.", []),
    "NexusEchoes": (PEOPLE, "Leave a spoken message at a spot", "Find an echo rune.", []),
    "NexusBathroom": (STRANGE, "A silly bathroom meter", "Hold on a while.", []),
    "NexusHouses": (PEOPLE, "Found a noble House with a sigil", "Start a House.", []),
    "NexusWarfare": (PEOPLE, "Houses fight to hold keeps", "Look for a keep.", []),
    "NexusMorality": (PEOPLE, "Help hurt survivors for karma", "Find an injured survivor.", []),
    "NexusFolklore": (STRANGE, "Real deaths become legends", "Die, or watch something explode.", ["death:*"]),
    "NexusAmbience": (SERVER, "Living sounds fill the world", "Listen.", []),
    "NexusCitizens": (PEOPLE, "Villagers fill your towns", "Visit a claimed town.", []),
    "NexusFamily": (PEOPLE, "Marry and raise a baby villager", "Meet someone special.", []),
    "NexusChaos": (STRANGE, "Random pranks, always explained", "Wait; something will happen.", []),
    "NexusDecor": (BUILD, "Claim a room, decorate, show it off", "Claim a room.", []),
    "NexusWildcard": (STRANGE, "Rare harmless mob spectacles", "Keep your eyes open.", []),
    "NexusArrival": (STRANGE, "Spooky sights only you can see", "Play a long session.", []),
    "NexusChronicle": (PEOPLE, "A server-wide story in chapters", "Follow the story.", []),
    "NexusThreshold": (GO, "Paths and scars appear where you walk", "Walk the same way often.", []),
    "NexusOracle": (MAGIC, "A prophecy from real server facts", "Seek the oracle.", []),
    "NexusKairos": (STRANGE, "Something reads the server's history", "Ask Kairos.", []),
    "NexusPennywise": (STRANGE, "It learns what kills you", "Die the same way twice.", []),
}

cat = yaml.safe_load(open(catalog, encoding="utf-8"))["systems"]
cmds = {}
def plugin_ymls(root):
    """Every plugin.yml up to five folders below ROOT (projects sit at ROOT/X or ROOT/x/X)."""
    root = os.path.abspath(root)
    for dirpath, dirs, files in os.walk(root):
        rel = os.path.relpath(dirpath, root)
        depth = 0 if rel == "." else rel.count(os.sep) + 1
        dirs[:] = [d for d in dirs if d not in ("node_modules", "refs", "integrations", "adminmenu", ".git") and depth < 5]
        if os.path.basename(dirpath) == "resources" and "plugin.yml" in files and dirpath.endswith(os.path.join("src", "main", "resources")):
            yield os.path.join(dirpath, "plugin.yml")

for f in plugin_ymls(plugins_dir):
    try:
        y = yaml.safe_load(open(f, encoding="utf-8"))
        cmds.setdefault(y["name"], list((y.get("commands") or {}).keys()))
    except Exception:
        pass

problems = []
for name in TABLE:
    if name not in cat:
        problems.append(f"TABLE names {name}, which is not in the catalog")
for name in cat:
    if name not in TABLE and name not in SKIP:
        problems.append(f"{name} is in the catalog but neither in TABLE nor in SKIP")
if problems:
    print("\n".join(problems))
    sys.exit(1)

def q(s):
    return json.dumps(s, ensure_ascii=False)

out = []
out.append("# NexusJournal codex: every page of the field journal. Generated by tools/gen-codex.py from NexusPath's catalog.yml")
out.append("# and the sibling plugins' plugin.yml files; replaced when the plugin updates. Make your own changes in")
out.append("# custom-codex.yml (it explains itself) and run /journal reload.")
out.append("")
out.append("entries:")
out.append("")
out.append("  welcome:")
out.append(f"    title: {q('Your Field Journal')}")
out.append("    icon: BOOK")
out.append(f"    group: {q(START)}")
out.append(f"    tip: {q('Pages fill in as you play')}")
out.append(f"    body: {q('This is your field journal. A page opens the first time you meet something on this server, and only for the parts of the game you chose. Nothing to hunt: just play.')}")
out.append(f"    hint: {q('Join the server.')}")
out.append("    triggers: [\"join\"]")
out.append("")
count_api = 0
for grp in GROUPS:
    for name, (g, tip, hint, trig) in TABLE.items():
        if g != grp:
            continue
        pid = name[5:].lower() if name.lower().startswith("nexus") else name.lower()
        triggers = list(trig)
        if cmds.get(name):
            triggers.insert(0, "command")
        if not triggers:
            triggers = ["api"]
            count_api += 1
        out.append(f"  {pid}:")
        out.append(f"    plugin: {name}")
        out.append(f"    title: {q(cat[name]['name'])}")
        out.append(f"    icon: {cat[name]['icon']}")
        out.append(f"    group: {q(g)}")
        out.append(f"    tip: {q(tip)}")
        out.append(f"    body: {q(cat[name]['blurb'])}")
        out.append(f"    hint: {q(hint)}")
        out.append("    triggers: [" + ", ".join(q(t) for t in triggers) + "]")
        out.append("")
dest = os.path.join(here, "..", "src", "main", "resources", "codex.yml")
open(dest, "w", encoding="utf-8").write("\n".join(out))
print(f"wrote {dest}: {len(TABLE) + 1} pages, {count_api} that only a plugin or staff can open")

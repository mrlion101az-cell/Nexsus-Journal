#!/usr/bin/env python3
"""Offline check of NexusJournal's built-in codex.yml (and, optionally, a custom-codex.yml).

  python3 tools/validate-codex.py [codex.yml] [--plugins DIR]

Checks, so a typo can never leave a page that silently never opens:
  * the shape: allowed keys only, page ids a-z 0-9 - _, every page has a title, tip, hint and group
  * every plugin named is the real name of a plugin (looked up in DIR's plugin.yml files and NexusTutorial's
    lessons.yml; default DIR: the folder above this project), and every real catalogued system has a page
  * every icon is a real item; text fits the tile and the action bar (title+tip <= 76 characters)
  * every trigger is a known kind with a sensible argument: block / item names that exist (a wildcard must match at
    least one), inventory, fish and bed names from the real Paper enums, entity names that exist; a command trigger
    names a plugin that really has commands, and any command it names really exists
  * says how many pages only a plugin or staff can open
Needs: pip install pyyaml
"""
import fnmatch, json, os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
args = [a for a in sys.argv[1:] if not a.startswith("--")]
plugins_dir = os.path.join(here, "..", "..")
if "--plugins" in sys.argv:
    plugins_dir = sys.argv[sys.argv.index("--plugins") + 1]
    args = [a for a in args if a != plugins_dir]
path = args[0] if args else os.path.join(here, "..", "src", "main", "resources", "codex.yml")
doc = yaml.safe_load(open(path, encoding="utf-8")) or {}
entries = doc.get("entries") or {}
errors, notes = [], []

PAGE_KEYS = {"plugin", "title", "icon", "group", "tip", "body", "hint", "triggers", "add-triggers"}
ROOT_KEYS = {"entries", "hidden-entries"}
KINDS = {"command", "break", "place", "craft", "use", "interact", "consume", "world", "breed", "kill", "death",
         "enchant", "fish", "sleep", "open", "bucket", "join", "api"}
GROUPS = {"Getting started", "Gather and make", "Farm and fish", "Magic and mystery", "Explore and travel",
          "Fight and survive", "Build and live", "People and places", "Strange and spooky", "Server life"}
# the real Paper enums (1.21): what the listener passes as the value
INVENTORY_TYPES = {"CHEST", "DISPENSER", "DROPPER", "FURNACE", "WORKBENCH", "CRAFTING", "ENCHANTING", "BREWING", "PLAYER",
                   "CREATIVE", "MERCHANT", "ENDER_CHEST", "ANVIL", "SMITHING", "BEACON", "HOPPER", "SHULKER_BOX", "BARREL",
                   "BLAST_FURNACE", "LECTERN", "SMOKER", "LOOM", "CARTOGRAPHY", "GRINDSTONE", "STONECUTTER", "COMPOSTER",
                   "CHISELED_BOOKSHELF", "JUKEBOX", "DECORATED_POT", "CRAFTER", "TRIAL_SPAWNER", "VAULT"}
FISH_STATES = {"FISHING", "CAUGHT_FISH", "CAUGHT_ENTITY", "IN_GROUND", "FAILED_ATTEMPT", "REEL_IN", "BITE"}
BED_RESULTS = {"OK", "NOT_POSSIBLE_HERE", "NOT_POSSIBLE_NOW", "TOO_FAR_AWAY", "NOT_SAFE", "OTHER_PROBLEM"}

data = os.environ.get("MCDATA", "/tmp/mcdata/node_modules/minecraft-data/minecraft-data/data/pc/1.21.4")
def names(fn):
    p = os.path.join(data, fn)
    if os.path.exists(p):
        return {i["name"].upper() for i in json.load(open(p, encoding="utf-8"))}
    notes.append(f"{fn} not found: skipped the checks that need it")
    return None
items = names("items.json")
blocks = names("blocks.json")
entities = names("entities.json")
placeable = (items or set()) | (blocks or set())

def plugin_ymls(root):
    root = os.path.abspath(root)
    for dirpath, dirs, files in os.walk(root):
        rel = os.path.relpath(dirpath, root)
        depth = 0 if rel == "." else rel.count(os.sep) + 1
        dirs[:] = [d for d in dirs if d not in ("node_modules", "refs", "integrations", "adminmenu", ".git") and depth < 5]
        if dirpath.endswith(os.path.join("src", "main", "resources")) and "plugin.yml" in files:
            yield os.path.join(dirpath, "plugin.yml")

real = {}   # plugin name -> set of command labels and aliases
for f in plugin_ymls(plugins_dir):
    try:
        y = yaml.safe_load(open(f, encoding="utf-8"))
    except Exception:
        continue
    labels = real.setdefault(y["name"], set())
    for c, v in (y.get("commands") or {}).items():
        labels.add(str(c).lower())
        for a in (v or {}).get("aliases") or []:
            labels.add(str(a).lower())
lessons = os.path.join(plugins_dir, "NexusTutorial", "src", "main", "resources", "lessons.yml")
if os.path.exists(lessons):
    for lid, l in (yaml.safe_load(open(lessons, encoding="utf-8")).get("lessons") or {}).items():
        if isinstance(l, dict) and l.get("plugin"):
            real.setdefault(l["plugin"], set())
if not real:
    notes.append("no plugin.yml found: skipped the check against real plugin names")

for k in doc:
    if k not in ROOT_KEYS:
        errors.append(f"unknown top-level key '{k}'")
if len({k.lower() for k in entries}) != len(entries):
    errors.append("two page ids differ only in case")

def glob_hits(arg, universe):
    """Does this |-separated wildcard match at least one real name?"""
    for alt in arg.split("|"):
        alt = alt.strip().upper()
        if alt and any(fnmatch.fnmatchcase(n, alt) for n in universe):
            return True
    return False

def every_alt_hits(arg, universe, pid, kind):
    for alt in arg.split("|"):
        alt = alt.strip()
        if alt and not any(fnmatch.fnmatchcase(n, alt.upper()) for n in universe):
            errors.append(f"page {pid}: {kind}:{alt} matches nothing that exists")

api_only = []
for pid, e in entries.items():
    if not re.fullmatch(r"[a-z0-9_-]+", str(pid)):
        errors.append(f"page id '{pid}' may use only a-z 0-9 - _")
    if not isinstance(e, dict):
        errors.append(f"page {pid}: not a section")
        continue
    for k in e:
        if k not in PAGE_KEYS:
            errors.append(f"page {pid}: unknown key '{k}'")
    for k in ("title", "tip", "hint", "group", "icon", "body", "triggers"):
        if not e.get(k):
            errors.append(f"page {pid}: missing {k}")
    title, tip, body, hint = (str(e.get(k, "")) for k in ("title", "tip", "body", "hint"))
    if len(title) > 32:
        errors.append(f"page {pid}: title longer than 32 characters")
    if len(tip) > 44:
        errors.append(f"page {pid}: tip longer than 44 characters")
    if len(title) + len(tip) > 76:
        errors.append(f"page {pid}: title + tip longer than 76 characters (the action bar clips)")
    if len(body) > 200:
        errors.append(f"page {pid}: body longer than 200 characters")
    if len(hint) > 60:
        errors.append(f"page {pid}: hint longer than 60 characters")
    if e.get("group") and e["group"] not in GROUPS:
        errors.append(f"page {pid}: group '{e['group']}' is not one of the standard groups")
    if items is not None and str(e.get("icon", "")).upper() not in items:
        errors.append(f"page {pid}: icon {e.get('icon')} is not a real item")
    plugin = e.get("plugin")
    if plugin and real and plugin not in real:
        errors.append(f"page {pid}: plugin '{plugin}' is not a real plugin")
    trig = e.get("triggers")
    if not isinstance(trig, list):
        errors.append(f"page {pid}: triggers must be a list")
        continue
    seen = set()
    for t in trig:
        t = str(t).strip()
        if t in seen:
            errors.append(f"page {pid}: trigger '{t}' is listed twice")
        seen.add(t)
        kind, _, arg = t.partition(":")
        arg = arg.strip() or "*"
        if kind not in KINDS:
            errors.append(f"page {pid}: '{kind}' is not a kind of trigger")
            continue
        if kind == "api" and len(trig) > 1:
            errors.append(f"page {pid}: api means 'nothing a player does'; it cannot be mixed with other triggers")
        if kind == "command":
            if not plugin:
                errors.append(f"page {pid}: a command trigger needs a plugin")
            elif real and plugin in real:
                if not real[plugin]:
                    errors.append(f"page {pid}: plugin {plugin} has no commands, so 'command' can never open it")
                elif arg != "*":
                    for alt in arg.split("|"):
                        alt = alt.strip().lower()
                        if "*" not in alt and alt not in real[plugin]:
                            errors.append(f"page {pid}: {plugin} has no command '{alt}'")
        if arg == "*":
            continue
        if kind in ("break", "place") and blocks is not None:
            every_alt_hits(arg, blocks, pid, kind)
        elif kind in ("craft", "use", "consume", "enchant", "bucket") and items is not None:
            every_alt_hits(arg, items, pid, kind)
        elif kind == "interact" and blocks is not None:
            every_alt_hits(arg, blocks, pid, kind)
        elif kind == "open":
            every_alt_hits(arg, INVENTORY_TYPES, pid, kind)
        elif kind == "fish":
            every_alt_hits(arg, FISH_STATES, pid, kind)
        elif kind == "sleep":
            every_alt_hits(arg, BED_RESULTS, pid, kind)
        elif kind in ("kill", "breed") and entities is not None:
            every_alt_hits(arg, entities, pid, kind)
    if all(str(t).split(":")[0].strip() == "api" for t in trig):
        api_only.append(pid)

# every system in NexusPath's catalogue that players can meet has a page
catalog = os.path.join(plugins_dir, "NexusPath", "src", "main", "resources", "catalog.yml")
SKIP = {"NexusLogs", "NexusCmdTracker", "NexusGate", "NexusMobShield", "NexusNPC", "NexusPrison", "NexusPurgatory",
        "NexusTerra", "NexusAdmin", "NexusVault", "NexusWeatherReport", "NexusScan", "NexusShackles", "NexusDenyBlocks",
        "NexusSpawnShield", "NexusSpawn"}
if os.path.exists(catalog) and os.path.abspath(path).endswith("codex.yml") and "custom" not in os.path.basename(path):
    systems = (yaml.safe_load(open(catalog, encoding="utf-8")) or {}).get("systems") or {}
    paged = {str(e.get("plugin")) for e in entries.values() if isinstance(e, dict)}
    for s in systems:
        if s not in paged and s not in SKIP:
            errors.append(f"system {s} is in NexusPath's catalog but has no page (and is not a staff tool)")
    for s in SKIP:
        if s in paged:
            errors.append(f"{s} is a staff tool and should not have a page")
else:
    notes.append("NexusPath's catalog.yml not checked")

for n in notes:
    print("note:", n)
if errors:
    for e in errors:
        print("ERROR:", e)
    sys.exit(1)
print(f"note: {len(entries)} pages, {len(api_only)} that only a plugin or staff can open")
print("codex OK")

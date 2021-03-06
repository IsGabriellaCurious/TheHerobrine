package moe.gabriella.herobrine.game;

import lombok.Getter;
import lombok.Setter;
import me.gabriella.gabsgui.GUIItem;
import moe.gabriella.herobrine.events.GameStateUpdateEvent;
import moe.gabriella.herobrine.events.ShardCaptureEvent;
import moe.gabriella.herobrine.events.ShardStateUpdateEvent;
import moe.gabriella.herobrine.game.runnables.*;
import moe.gabriella.herobrine.kit.Kit;
import moe.gabriella.herobrine.kit.abilities.BatBombAbility;
import moe.gabriella.herobrine.kit.abilities.BlindingAbility;
import moe.gabriella.herobrine.kit.abilities.DreamweaverAbility;
import moe.gabriella.herobrine.kit.abilities.LocatorAbility;
import moe.gabriella.herobrine.kit.kits.ArcherKit;
import moe.gabriella.herobrine.kit.kits.PriestKit;
import moe.gabriella.herobrine.kit.kits.ScoutKit;
import moe.gabriella.herobrine.kit.kits.WizardKit;
import moe.gabriella.herobrine.data.RedisManager;
import moe.gabriella.herobrine.stat.StatManager;
import moe.gabriella.herobrine.stat.StatTracker;
import moe.gabriella.herobrine.utils.*;
import moe.gabriella.herobrine.world.WorldManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;

public class GameManager {

    @Getter private JavaPlugin plugin;

    private static GameManager instance;
    private WorldManager worldManager;
    private RedisManager redis;

    @Getter private GameState gameState;
    @Getter private ShardState shardState;

    @Getter private int requiredToStart;
    @Getter private int maxPlayers;
    @Getter private boolean allowOverfill;
    @Getter private int startingDuration;

    @Getter private String networkName;

    @Getter private Player herobrine;
    private BatBombAbility hbBatBomb;
    private DreamweaverAbility hbDream;
    private BlindingAbility hbBlinding;
    @Getter private ArrayList<Player> survivors;
    @Getter private ArrayList<Player> spectators;
    @Getter private Player passUser;

    @Getter public int shardCount;
    @Getter @Setter private Player shardCarrier;

    public int startTimer = 15; //todo set to 90
    public boolean stAlmost = false;
    public boolean stFull = false;

    @Getter private Kit[] kits;
    @Getter private Kit defaultKit;
    @Getter private HashMap<Player, Kit> playerKits;

    @Getter @Setter private StatTracker[] statTrackers;

    public GameManager(JavaPlugin plugin, WorldManager worldManager, RedisManager redis) {
        Console.info("Loading Game Manager...");
        this.plugin = plugin;
        instance = this;
        this.worldManager = worldManager;
        this.redis = redis;
        plugin.getServer().getPluginManager().registerEvents(new GMListener(this), plugin);

        gameState = GameState.BOOTING;
        shardState = ShardState.WAITING;

        requiredToStart = plugin.getConfig().getInt("minPlayers");
        maxPlayers = plugin.getConfig().getInt("maxPlayers");
        allowOverfill = plugin.getConfig().getBoolean("allowOverfill");
        networkName = plugin.getConfig().getString("networkName");

        shardCount = 0;
        survivors = new ArrayList<>();
        spectators = new ArrayList<>();

        kits = new Kit[] {
                new ArcherKit(this),
                new PriestKit(this),
                new ScoutKit(this),
                new WizardKit(this)
        };

        for (Kit k : kits) {
            if (k.getInternalName().equals("archer"))
                defaultKit = k;
        }

        playerKits = new HashMap<>();

        startWaiting();
        Console.info("Game Manager is ready!");
    }

    public static GameManager get() { return instance; }

    public void setGameState(GameState newState) {
        new BukkitRunnable() {
            @Override
            public void run() {
                GameState old = gameState;
                if (old == null) old = GameState.UNKNOWN;

                gameState = newState;
                Console.info("Game state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager().callEvent(new GameStateUpdateEvent(old, newState));
            }
        }.runTask(plugin);
    }

    public void setShardState(ShardState newState) {
        new BukkitRunnable() {

            @Override
            public void run() {
                ShardState old = shardState;
                if (old == null) old = ShardState.UNKNOWN;

                shardState = newState;
                Console.info("Shard state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager().callEvent(new ShardStateUpdateEvent(old, newState));
                if (gameState == GameState.LIVE)
                    NarrationRunnable.timer = 0;
            }
        }.runTask(plugin);
    }

    public void startWaiting() {
        setGameState(GameState.WAITING);
        new WaitingRunnable().runTaskTimerAsynchronously(plugin, 0, 10);
    }

    public void start() {
        setGameState(GameState.LIVE);
        new NarrationRunnable().runTaskTimerAsynchronously(plugin, 0, 10); // has to run before the shardstate updates
        setShardState(ShardState.WAITING);
        StatManager.get().startTracking();
        if (passUser != null) {
            herobrine = passUser;
            passUser = null;
        } else {
            herobrine = PlayerUtil.randomPlayer();
        }
        survivors.remove(herobrine);
        setupHerobrine();
        setupSurvivors();
        new HerobrineSetup().runTaskAsynchronously(plugin);
        for (Player p : survivors) {
            new SurvivorSetup(p).runTaskAsynchronously(plugin);
        }
        new ShardHandler().runTaskTimer(plugin, 0, 20);
        new HerobrineItemHider().runTaskTimer(plugin, 0, 1);
        new HerobrineSmokeRunnable().runTaskTimer(plugin, 0, 10); //todo this needs working on, its very tps heavy
    }

    public void setupHerobrine() {
        herobrine.teleport(worldManager.herobrineSpawn);

        PlayerUtil.addEffect(herobrine, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.JUMP, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false);

        updateHerobrine();
    }

    public void updateHerobrine() {
        switch (shardCount) {
            case 0: {
                GUIItem item = new GUIItem(Material.STONE_AXE).displayName(ChatColor.GRAY + "The Thorbringer");
                herobrine.getInventory().setItem(0, item.build());

                hbBatBomb = new BatBombAbility(this, 1, 4);
                hbBatBomb.apply(herobrine);
                plugin.getServer().getPluginManager().registerEvents(hbBatBomb, plugin);

                giveVials(2, 1);

                hbDream = new DreamweaverAbility(this, 3, 2);
                hbDream.apply(herobrine);
                plugin.getServer().getPluginManager().registerEvents(hbDream, plugin);

                hbBlinding = new BlindingAbility(this, -1, 3);
                plugin.getServer().getPluginManager().registerEvents(hbBlinding, plugin);

                new LocatorAbility(this).apply(herobrine);
                break;
            }
            case 1: {
                herobrine.getInventory().remove(Material.STONE_AXE);
                GUIItem item = new GUIItem(Material.IRON_AXE).displayName(ChatColor.GRAY + "Axe of " + ChatColor.BOLD + "Deceit!");
                herobrine.getInventory().addItem(item.build());

                hbBlinding.apply(herobrine);

                giveVials(-1, 2);

                break;
            }
            case 2: {
                herobrine.getInventory().remove(Material.IRON_AXE);
                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName(ChatColor.GRAY + "Sword of " + ChatColor.BOLD + "HELLBRINGING!");
                herobrine.getInventory().addItem(item.build());

                hbBatBomb.slot = -1;
                hbBatBomb.amount = 3;
                hbBatBomb.apply(herobrine);

                hbDream.slot = -1;
                hbDream.amount = 1;
                hbDream.apply(herobrine);

                giveVials(-1, 2);
                break;
            }
            case 3: {
                herobrine.getInventory().clear();

                giveVials(-1, 2);

                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName(ChatColor.AQUA + "Sword of " + ChatColor.BOLD + "Chances!");
                herobrine.getInventory().addItem(item.build());
                break;
            }
        }
    }

    public void giveVials(int slot, int amount) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        potion.setAmount(amount);
        PotionMeta pm = (PotionMeta) potion.getItemMeta();

        pm.setDisplayName(ChatColor.GREEN + "Poisonous Vial");

        pm.setBasePotionData(new PotionData(PotionType.POISON, false, true));
        potion.setItemMeta(pm);

        if (slot == -1)
            herobrine.getInventory().addItem(potion);
        else
            herobrine.getInventory().setItem(slot, potion);
    }

    public void setupSurvivors() {
        setupKits();
        applyKits();
        for (Player p : survivors) {
            p.teleport(worldManager.survivorSpawn);
            PlayerUtil.addEffect(p, PotionEffectType.BLINDNESS, 60, 1, false, false);
        }
    }

    public void makeSpectator(Player player) {
        PlayerUtil.clearInventory(player);
        PlayerUtil.clearEffects(player);

        spectators.add(player);

        for (Player p : spectators) {
            p.showPlayer(plugin, player);
            player.showPlayer(plugin, p);
        }

        for (Player p : survivors)
            p.hidePlayer(plugin, player);
        herobrine.hidePlayer(plugin, player);

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.teleport(worldManager.survivorSpawn);
    }

    public void end(WinType type) {
        setGameState(GameState.ENDING);
        setShardState(ShardState.INACTIVE);
        voidKits();
        if (type == WinType.SURVIVORS) {
            PlayerUtil.broadcastTitle(ChatColor.GREEN + "SURVIVORS WIN!", "", 20, 60, 20);
            Message.broadcast(Message.format("" + ChatColor.GREEN + ChatColor.BOLD + "The Survivors " + ChatColor.YELLOW + "have defeated " + ChatColor.RED + ChatColor.BOLD + "The Herobrine!"));
            Message.broadcast(Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(Sound.ENTITY_WITHER_DEATH, 1f, 1f);
            for (Player p : survivors)
                StatManager.get().pointsTracker.increment(p.getUniqueId(), 10);
        } else {
            PlayerUtil.broadcastTitle(ChatColor.RED + "HEROBRINE " + ChatColor.GREEN + " WINS!", "", 20, 60, 20);
            Message.broadcast(Message.format("" + ChatColor.RED + ChatColor.BOLD + "The Herobrine " + ChatColor.YELLOW + "has defeated all the survivors"));
            Message.broadcast(Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 1f);
            StatManager.get().pointsTracker.increment(herobrine.getUniqueId(), 10);
        }
        StatManager.get().push();
        StatManager.get().stopTracking();
    }

    public void endCheck() {
        if (getSurvivors().size() == 0) {
            end(WinType.HEROBRINE);
        } else if (!getHerobrine().isOnline()) {
            end(WinType.SURVIVORS);
        }
    }

    public void capture(Player player) {
        player.getInventory().remove(Material.NETHER_STAR);
        shardCarrier = null;
        shardCount++;
        if (shardCount == 3) {
            setShardState(ShardState.INACTIVE);
            herobrine.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        else
            setShardState(ShardState.WAITING);
        new CaptureSequence(player).runTaskAsynchronously(plugin);
        updateHerobrine();
        Bukkit.getServer().getPluginManager().callEvent(new ShardCaptureEvent(player));
    }

    public double getSurvivorHitDamage(Material item, boolean strength) {
        double finalDamage = 0;
        double shardModifier = 0;
        double strengthModifier = 0;
        boolean normal = false;
        switch (item) {
            case IRON_AXE:
                finalDamage = 2.7; // 0 - 1.4 | 1 - 2.15 | 2 - 3.5 | 3 - 3.5
                shardModifier = 1.5;
                strengthModifier = 1.5;
                break;
            case STONE_SWORD:
                finalDamage = 2.5; // 0 - 1.25 | 1 - 2.25 | 2 - 3.25 | 3 - 4.25
                shardModifier = 2;
                strengthModifier = 1.6;
                break;
            case WOODEN_SWORD:
                finalDamage = 2.3; // 0 - 1.35 | 1 - 1.55 | 2 - 2.49 | 3 - 3.2
                shardModifier = 1.3;
                strengthModifier = 2.3;
                break;
            case IRON_SWORD:
                finalDamage = 2.9; // 0 - 1.49 | 1 - 2.5 | 2 - 3.57 | 3 - 4.75
                shardModifier = 2.2;
                strengthModifier = 1.3;
                break;
            default:
                normal = true;
                break;
        }

        if (normal)
            return -1;

        if (shardCount > 0)
            finalDamage += (shardModifier * shardCount);

        if (strength)
            finalDamage += strengthModifier;

        return finalDamage;
    }

    public double getHerobrineHitDamage(Material item) {
        double finalDamage = 0;
        switch (item) {
            case STONE_AXE:
                finalDamage = 2.5; // 0 - 1.25
                break;
            case IRON_AXE:
                finalDamage = 3.5; // 1 - 2.75
                break;
            case IRON_SWORD:
                finalDamage = (shardCount == 2 ? 4.5 : 6.5); // 2 - 2.25 | 3 - 3.25
                break;
            default:
                return -1;
        }

        return finalDamage;
    }
    
    public void hubInventory(Player player) {
        PlayerUtil.clearEffects(player);
        PlayerUtil.clearInventory(player);

        GUIItem kitItem = new GUIItem(Material.COMPASS);
        kitItem.displayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Choose " + ChatColor.AQUA + ChatColor.BOLD + "Class");
        player.getInventory().setItem(0, kitItem.build());
    }

    // Kits
    public void setupKits() {
        for (Kit kit : kits) {
            plugin.getServer().getPluginManager().registerEvents(kit, plugin);
        }
    }

    public void voidKits() {
        for (Kit kit : kits) {
            HandlerList.unregisterAll(kit);

            kit.voidAbilities();
        }

        HandlerList.unregisterAll(hbBatBomb);
        HandlerList.unregisterAll(hbDream);
        HandlerList.unregisterAll(hbBlinding);
    }

    public void applyKits() {
        for (Player p : survivors) {
            Kit k = getLocalKit(p);
            k.apply(p);
        }
    }

    public void setKit(Player player, Kit kit, boolean inform) {
        playerKits.remove(player);
        playerKits.put(player, kit);
        saveKit(player, kit);

        if (inform)
            player.sendMessage(Message.format(ChatColor.YELLOW + "Set your class to " + kit.getDisplayName()));
    }

    public void saveKit(Player player, Kit kit) {
        redis.setKey("hb:kit:" + player.getUniqueId().toString(), kit.getInternalName());
    }

    public Kit getSavedKit(Player player) {
        String key = "hb:kit:" + player.getUniqueId().toString();
        if (!redis.exists(key))
            return defaultKit;

        String result = redis.getKey(key);
        for (Kit k : kits)
            if (k.getInternalName().equals(result))
                return k;

        return defaultKit;
    }

    public Kit getLocalKit(Player player) {
        if (!playerKits.containsKey(player)) {
            setKit(player, defaultKit, false);
            return defaultKit;
        } else {
            return playerKits.get(player);
        }
    }

}

package moe.gabriella.herobrine.stat;

import moe.gabriella.herobrine.data.SqlManager;
import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.stat.trackers.CaptureTracker;
import moe.gabriella.herobrine.stat.trackers.DeathTracker;
import moe.gabriella.herobrine.stat.trackers.KillsTracker;
import moe.gabriella.herobrine.stat.trackers.PointsTracker;
import moe.gabriella.herobrine.utils.Console;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatManager {

    private boolean nullBool;

    private JavaPlugin plugin;
    public GameManager gm;
    private static StatManager instance;

    public StatTracker pointsTracker;

    public HashMap<UUID, Integer> points;
    public HashMap<UUID, GameRank> gameRanks;

    public StatManager(JavaPlugin plugin, GameManager gm) {
        Console.info("Loading Stat Manager...");
        this.plugin = plugin;
        this.gm = gm;
        instance = this;

        gm.setStatTrackers(new StatTracker[] {
                new PointsTracker(this),
                new CaptureTracker(this),
                new KillsTracker(this),
                new DeathTracker(this)
        });

        for (StatTracker tracker : gm.getStatTrackers()) {
            if (tracker.getInternalName().equals("points")) {
                pointsTracker = tracker;
                break;
            }
        }

        points = new HashMap<>();
        gameRanks = new HashMap<>();
        Console.info("Stat Manager is ready!");
    }

    public static StatManager get() { return instance; }

    public void startTracking() {
        for (StatTracker tracker : gm.getStatTrackers()) {
            tracker.start();
        }
    }

    public void stopTracking() {
        for (StatTracker tracker : gm.getStatTrackers()) {
            tracker.reset();
        }
    }

    public void push() {
        Console.info("Pushing stats...");

        for (StatTracker tracker : gm.getStatTrackers()) {
            for (Map.Entry<UUID, Integer> entry : tracker.stat.entrySet()) {
                UUID uuid = entry.getKey();
                int stat = entry.getValue();

                int curr = getCurrentStat(uuid, tracker);
                if (curr == -1) {
                    Console.error("Error pushing stat, previous was -1 for " + uuid + "!");
                    continue;
                }

                setStat(uuid, tracker.getInternalName(), curr, stat);
            }
        }

        Console.info("Stats pushed!");
    }

    private void setStat(UUID uuid, String name, int prev, int amount) {
        try {
            Connection connection = SqlManager.get().createConnection();

            PreparedStatement statement = connection.prepareStatement("UPDATE `hb_stat` SET " + name + "=? WHERE uuid=?");
            int next = prev + amount;

            statement.setInt(1, next);
            statement.setString(2, uuid.toString());

            statement.executeUpdate();

            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getCurrentStat(UUID uuid, StatTracker stat) {
        try {
            Connection connection = SqlManager.get().createConnection();

            PreparedStatement statement = connection.prepareStatement("SELECT `" + stat.getInternalName() + "` FROM `hb_stat` WHERE uuid=?");
            statement.setString(1, uuid.toString());
            ResultSet rs = statement.executeQuery();

            int result;
            if (rs.next()) {
                result = rs.getInt(stat.getInternalName());
            } else {
                result = -1;
            }

            connection.close();

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private boolean exists(UUID uuid) {
        try {
            Connection connection = SqlManager.get().createConnection();

            PreparedStatement statement = connection.prepareStatement("SELECT * FROM `hb_stat` WHERE uuid=?");
            statement.setString(1, uuid.toString());
            ResultSet rs = statement.executeQuery();

            boolean result = rs.next();
            connection.close();

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return nullBool;
        }
    }

    private void create(UUID uuid) {
        try {
            Connection connection = SqlManager.get().createConnection();

            PreparedStatement statement = connection.prepareStatement("INSERT INTO `hb_stat` (uuid) VALUE (?)");
            statement.setString(1, uuid.toString());
            statement.executeUpdate();

            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void check(UUID uuid) {
        if (!exists(uuid))
            create(uuid);

        int p = getCurrentStat(uuid, pointsTracker);
        points.put(uuid, p);
        gameRanks.put(uuid, GameRank.findRank(p));
    }

    public GameRank getGameRank(UUID uuid) {
        return gameRanks.get(uuid);
    }

}

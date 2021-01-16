package moe.gabriella.herobrine.game.runnables;

import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.utils.GameState;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

public class HerobrineSmokeRunnable extends BukkitRunnable {

    GameManager gm = GameManager.getInstance();

    @Override
    public void run() {
        if (gm.getGameState() != GameState.LIVE) {
            cancel();
            return;
        }

        Location loc = gm.getHerobrine().getLocation().clone().add(0, 0, 1);
        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc.getX(), loc.getY(), loc.getZ(), 1);
    }
}

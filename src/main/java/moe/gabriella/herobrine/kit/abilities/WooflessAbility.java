package moe.gabriella.herobrine.kit.abilities;

import me.gabriella.gabsgui.GUIItem;
import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.kit.KitAbility;
import moe.gabriella.herobrine.utils.GameState;
import moe.gabriella.herobrine.utils.PlayerUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class WooflessAbility extends KitAbility {

    public int slot;
    public Player player;

    public WooflessAbility(GameManager gm, int slot) {
        super(gm, "Woofless");
        this.slot = slot;
    }

    @Override
    public void apply(Player player) {
        this.player = player;

        GUIItem bone = new GUIItem(Material.BONE).displayName(ChatColor.DARK_GREEN + "Summon Woofless");
        player.getInventory().setItem(slot, bone.build());
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (gm.getGameState() != GameState.LIVE)
            return;

        Player player = event.getPlayer();

        if (this.player != player)
            return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (player.getInventory().getItemInMainHand().getType() == Material.BONE) {
                Wolf wolf = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);
                wolf.setTamed(true);
                wolf.setOwner(player);
                PlayerUtil.playSoundAt(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1f, 1f);
                player.getInventory().remove(Material.BONE);
            }
        }
    }
}

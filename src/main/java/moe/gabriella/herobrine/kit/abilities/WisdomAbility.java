package moe.gabriella.herobrine.kit.abilities;

import me.gabriella.gabsgui.GUIItem;
import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.kit.KitAbility;
import moe.gabriella.herobrine.utils.Console;
import moe.gabriella.herobrine.utils.GameState;
import moe.gabriella.herobrine.utils.PlayerUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class WisdomAbility extends KitAbility {

    public int slot;
    public int amount;
    public Player player;

    public WisdomAbility(GameManager gm, int slot, int amount) {
        super(gm, "Notch's Wisdom");
        this.slot = slot;
        this.amount = amount;
    }

    @Override
    public void apply(Player player) {
        this.player = player;
        GUIItem wiz = new GUIItem(Material.BLAZE_POWDER).displayName(ChatColor.GREEN + "Notch's Wisdom").amount(amount);

        player.getInventory().setItem(slot, wiz.build());
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (gm.getGameState() != GameState.LIVE)
            return;

        Player player = event.getPlayer();

        if (this.player != player)
            return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (player.getInventory().getItemInMainHand().getType() == Material.BLAZE_POWDER) {
                PlayerUtil.removeAmountOfItem(player, player.getInventory().getItemInMainHand(), 1);
                new WisdomHandler(player.getLocation()).runTaskTimerAsynchronously(gm.getPlugin(), 0, 20);
            }
        }
    }
}

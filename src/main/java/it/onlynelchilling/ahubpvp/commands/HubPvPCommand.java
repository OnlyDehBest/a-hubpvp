package it.onlynelchilling.ahubpvp.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import it.onlynelchilling.ahubpvp.HubPvPSword;
import it.onlynelchilling.ahubpvp.utils.MessageUtils;
import org.bukkit.entity.Player;

@CommandAlias("hubpvp")
@CommandPermission("hubpvp.admin")
public final class HubPvPCommand extends BaseCommand {

    private final HubPvPSword plugin;

    public HubPvPCommand(HubPvPSword plugin) {
        this.plugin = plugin;
    }

    @Default
    @Subcommand("reload")
    @CommandPermission("hubpvp.reload")
    public void onReload(Player player) {
        plugin.getConfigCache().reload();

        MessageUtils.send(player, plugin.getConfigCache().getMessage("config-reloaded"));
    }
}

package pl.example.billboardrefresher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final BillboardRefresherPlugin plugin;

    public ReloadCommand(BillboardRefresherPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("billboardrefresher.reload")) {
                sender.sendMessage("§cYou don't have permission to do that.");
                return true;
            }
            plugin.reload();
            sender.sendMessage("§aBillboardRefresher config reloaded.");
            return true;
        }
        sender.sendMessage("§eUsage: /billboardrefresher reload");
        return true;
    }
}

package com.example.basewar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JoinCommand implements CommandExecutor {

    private final GameManager gameManager;

    public JoinCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 팀에 참여할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("사용법: /join <red|blue|yellow|green>");
            return true;
        }

        Player player = (Player) sender;
        try {
            Team team = Team.valueOf(args[0].toUpperCase());
            gameManager.addPlayerToTeam(player, team);
        } catch (IllegalArgumentException e) {
            player.sendMessage("존재하지 않는 팀입니다. <red|blue|yellow|green>");
        }

        return true;
    }
}

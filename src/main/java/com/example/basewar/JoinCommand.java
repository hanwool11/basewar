package com.example.basewar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JoinCommand implements CommandExecutor, TabCompleter {

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> teamNames = Arrays.stream(Team.values())
                                         .map(team -> team.name().toLowerCase())
                                         .collect(Collectors.toList());
            for (String teamName : teamNames) {
                if (teamName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(teamName);
                }
            }
        }
        return completions;
    }
}
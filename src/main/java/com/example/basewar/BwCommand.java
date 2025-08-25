package com.example.basewar;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BwCommand implements CommandExecutor {

    private final GameManager gameManager;

    public BwCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e사용법: /bw <start|stop|config>");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            gameManager.startGame();
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            gameManager.stopGame(true);
            return true;
        }

        if (args[0].equalsIgnoreCase("config")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("setspawn")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Location location = player.getLocation();
                    gameManager.setGlobalSpawn(location);
                    sender.sendMessage("§a게임 시작 스폰 지점을 현재 위치로 설정했습니다.");
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
            } else {
                sender.sendMessage("§e사용법: /bw config <setspawn>");
            }
            return true;
        }

        sender.sendMessage("§c알 수 없는 명령어입니다. 사용법: /bw <start|stop|config>");
        return true;
    }
}

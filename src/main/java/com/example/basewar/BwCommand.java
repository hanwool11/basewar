package com.example.basewar;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BwCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public BwCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e사용법: /bw <start|stop|config|team>");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            gameManager.startGame();
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            gameManager.stopGame(true);
            return true;
        }

        if (args[0].equalsIgnoreCase("team")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                gameManager.toggleTeamChat(player);
            } else {
                sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("config")) {
            if (!sender.hasPermission("basewar.admin")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§e사용법: /bw config <setspawn|inv|kit>");
                return true;
            }

            if (args[1].equalsIgnoreCase("setspawn")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Location location = player.getLocation();
                    gameManager.setGlobalSpawn(location);
                    sender.sendMessage("§a게임 시작 스폰 지점을 현재 위치로 설정했습니다.");
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("inv")) {
                if (args.length < 3) {
                    sender.sendMessage("§e사용법: /bw config inv <시간(초)>");
                    return true;
                }
                try {
                    int duration = Integer.parseInt(args[2]);
                    if (duration < 0) {
                        sender.sendMessage("§c시간은 0 이상이어야 합니다.");
                        return true;
                    }
                    gameManager.setInvincibilityDuration(duration);
                    sender.sendMessage("§a무적 시간이 " + duration + "초로 설정되었습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c유효한 숫자를 입력해주세요.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("kit")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    gameManager.openKitGui(player);
                } else {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                }
                return true;
            }

            sender.sendMessage("§e사용법: /bw config <setspawn|inv|kit>");
            return true;
        }

        sender.sendMessage("§c알 수 없는 명령어입니다. 사용법: /bw <start|stop|config|team>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Only provide tab completion for OPs
        if (!sender.hasPermission("basewar.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("start", "stop", "config", "team");
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            List<String> configSubcommands = Arrays.asList("setspawn", "inv", "kit");
            for (String sub : configSubcommands) {
                if (sub.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("inv")) {
            // Suggest a default time for invincibility
            completions.add("900"); // Current default is 900 seconds (15 minutes)
        }
        return completions;
    }
}
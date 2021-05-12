package red.man10.man10lockettepass;

import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

///////////////////////////////////////////////////////////////////////////
// Tab補充関連です(´･ω･`)
///////////////////////////////////////////////////////////////////////////

public final class TabComplete implements TabCompleter {

    private final Man10LockettePass plugin;

    public TabComplete(Man10LockettePass plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 0) {
            return completions;
        }
        if (args.length == 1) {
            checkAdd(sender, commands, "set", "get", "list", "reload", "passmap");
            commands.add("help");
            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        else if (canAdd(args[0], "set", sender)) {
            if (args.length == 2) {
                for (PassMap pass : plugin.getPasses(sender)) {
                    commands.add(pass.getUUID().toString());
                }
                if (sender instanceof Player && plugin.getSelects().containsKey(sender)) {
                    commands.add(String.valueOf(new Random().nextInt(1000000)));
                }
                StringUtil.copyPartialMatches(args[1], commands, completions);
            } else if (args.length == 3) {
                if (Man10LockettePass.matchUUID(args[1])) {
                    commands.add(String.valueOf(new Random().nextInt(1000000)));
                }
                StringUtil.copyPartialMatches(args[2], commands, completions);
            }
        }
        else if (canAdd(args[0], "get", sender)) {
            if (args.length == 2) {
                commands.addAll(plugin.getPassMapsToDisplay('&'));
                StringUtil.copyPartialMatches(args[1], commands, completions);
            } else if (args.length == 3) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    commands.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[2], commands, completions);
            }
        }
        else if (canAdd(args[0], "passmap", sender)) {
            if (args.length == 2) {
                checkAdd(sender, commands, "create", "delete", "list");
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
            else if (canAdd(args[1], "create", sender)) {
                if (args.length == 3) {
                    commands.addAll(MappRenderer.imageMap.keySet());
                    StringUtil.copyPartialMatches(args[2], commands, completions);
                } else if (args.length == 4) {
                    if (MappRenderer.imageMap.containsKey(args[2])) {
                        List<String> passes = plugin.getPassMapsToDisplay('&');
                        String pass = args[2];
                        if (passes.contains(pass)) {
                            pass += "_" + passes.stream().filter(text -> text.startsWith(args[2])).count();
                        }
                        commands.add(pass);
                        StringUtil.copyPartialMatches(args[3], commands, completions);
                    }
                }
            }
            else if (canAdd(args[1], "delete", sender)) {
                if (args.length == 3) {
                    commands.addAll(plugin.getPassMapsToDisplay('&'));
                    StringUtil.copyPartialMatches(args[2], commands, completions);
                }
            }
        }
        Collections.sort(completions);
        return completions;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            そのコマンドを実行する為に必要な条件を全て満たしているかを確認します。
    public boolean canAdd(String argument, String command, CommandSender sender) {
        return Man10LockettePass.equals(argument, command)
                && sender.hasPermission("mlockette.command." + command);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            そのコマンドのいずれかが実行する為に必要な条件を全て満たしているのかを確認します。
    public static void checkAdd(CommandSender sender, List<String> commands, String... args) {
        for (String arg : args) {
            if (!sender.hasPermission("mlockette.command." + arg)) continue;
            commands.add(arg);
        }
    }
}

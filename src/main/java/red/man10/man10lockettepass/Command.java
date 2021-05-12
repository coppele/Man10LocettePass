package red.man10.man10lockettepass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static red.man10.man10lockettepass.SendMessage.sendAdviceMessage;
import static red.man10.man10lockettepass.SendMessage.sendErrorMessage;
import static red.man10.man10lockettepass.SendMessage.sendFailedMessage;
import static red.man10.man10lockettepass.SendMessage.sendInfoMessage;
import static red.man10.man10lockettepass.SendMessage.sendSuccessMessage;
import static red.man10.man10lockettepass.SendMessage.sendWarningMessage;

///////////////////////////////////////////////////////////////////////////
// コマンド関連です(´･ω･`)
///////////////////////////////////////////////////////////////////////////

public final class Command implements CommandExecutor {

    private final Man10LockettePass plugin;
    private final boolean isJoke;

    public Command(Man10LockettePass plugin, boolean isJoke) {
        this.plugin = plugin;
        this.isJoke = isJoke;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        if (Man10LockettePass.equals(command.getName(), "mlock")) {
            if (args.length == 0 || Man10LockettePass.equals(args[0], "help")) {
                sendCommandHelp(sender);
                return true;
            }
            if (canExecuteCommand(args[0], "reload", sender)) {
                plugin.reload();
                sendSuccessMessage(sender, "リロードしました！");
                return true;
            }
            if (canExecuteCommand(args[0], "list", sender)) {
                int page = 1;
                if (args.length == 2) {
                    if (!NumberUtils.isNumber(args[1])) {
                        sendFailedMessage(sender, "開きたいページの数値を入力してください");
                        return true;
                    }
                    page = Integer.parseInt(args[1]);
                }
                sendPage(sender, plugin.getPasses(sender), 15, page, PassMap::toString);
                return true;
            }
            if (canExecuteCommand(args[0], "set", sender)) {
                if (args.length == 1) {
                    sendErrorMessage(sender, "mlock", "PassMap名(UUID)または選択した状態でパスワードを入力してください...");
                    return true;
                }
                // こちらがUUIDでのパスワード設定
                if (Man10LockettePass.matchUUID(args[1])) {
                    if (args.length == 2) {
                        sendErrorMessage(sender, "mlock", "パスワードを入力していません...");
                        return true;
                    }
                    if (plugin.getPass(UUID.fromString(args[1])) == null) {
                        sendFailedMessage(sender, args[1] + " は存在しないようです...");
                        return true;
                    }
                    PassMap pass = plugin.getPass(UUID.fromString(args[1]));
                    setPassword(sender, pass, args[2]);
                    return true;
                    // こちらがselectでのパスワード設定
                } else if (NumberUtils.isNumber(args[1])) {
                    if (!(sender instanceof Player)) {
                        sendErrorMessage(sender, "mlock", "PassMap名(UUID)を入力してください...");
                        return true;
                    }
                    if (!plugin.getSelects().containsKey(sender)) {
                        sendFailedMessage(sender, "PassMapを選択していません...");
                        return true;
                    }
                    PassMap pass = plugin.getSelects().get(sender);
                    setPassword(sender, pass, args[1]);
                    return true;
                }
                sendErrorMessage(sender, "mlock", "PassMap名(UUID)または選択した状態でパスワードを入力してください...");
                return true;
            }
            if (canExecuteCommand(args[0], "get", sender)) {
                if (args.length == 1) {
                    sendErrorMessage(sender, "mlock", "PassMap名を入力していません...");
                    return true;
                }
                ItemStack map = plugin.getPassMap(args[1]);
                if (map == null) {
                    sendFailedMessage(sender, args[1] + " は存在しないようです...");
                    return true;
                }
                if (args.length == 3) {
                    Player target = Bukkit.getServer().getPlayer(args[2]);
                    if (target == null) {
                        sendFailedMessage(sender, args[2] + "さん は存在しませんでした...");
                        return true;
                    }
                    giveItem(sender, target, args[1], map);
                    return true;
                }
                if (sender instanceof Player) {
                    giveItem(sender, (Player) sender, args[1], map);
                    return true;
                }
                sendSuccessMessage(sender, args[1] + "は正常に読み込まれています！");
                return true;
            }
            if (canExecuteCommand(args[0], "passmap", sender)) {
                if (args.length == 1) {
                    sendErrorMessage(sender, "mlock", "コマンドを入力していません...");
                    return true;
                }
                if (canExecuteCommand(args[1], "create", sender)) {
                    if (args.length == 2) {
                        sendErrorMessage(sender, "mlock", "PassMap名を入力していません...");
                        return true;
                    }
                    if (args.length == 3) {
                        sendErrorMessage(sender, "mlock", "表示名を入力していません...");
                        return true;
                    }
                    if (!MappRenderer.imageMap.containsKey(args[2])) {
                        sendFailedMessage(sender, "そのPassMap名は存在しません...");
                        return true;
                    }
                    Map<CommandSender, String[]> creates = plugin.getCreates();
                    if (plugin.getPassMapsToKey().contains(args[2])) {
                        if (plugin.getPassMapsToDisplay('&').contains(args[3])) {
                            sendFailedMessage(sender, "全く同じPassMapが存在しています...");
                            return true;
                        }
                        if (creates.containsKey(sender) && Arrays.equals(creates.get(sender), args)) {
                            List<String> lore = Arrays.asList(args).subList(4, args.length);
                            plugin.registerPassMap(args[2], args[3], lore);
                            creates.remove(sender);
                            sendSuccessMessage(sender, "作成しました！");
                            return true;
                        }
                        sendWarningMessage(sender, "本当に作成しますか？");
                        sendWarningMessage(sender, args[2] + " を使用したPassMapは既に作成済みのようです");
                        sendAdviceMessage(sender, "それでも作成する場合はもう一度同じコマンドを実行して見てください");
                        creates.put(sender, args);
                        return true;
                    }
                    List<String> lore = Arrays.asList(args).subList(4, args.length);
                    plugin.registerPassMap(args[2], args[3], lore);
                    creates.remove(sender);
                    sendSuccessMessage(sender, "作成しました！");
                    return true;
                }
                if (canExecuteCommand(args[1], "delete", sender)) {
                    if (args.length == 2) {
                        sendErrorMessage(sender, "mlock", "PassMap名を入力していません...");
                        return true;
                    }
                    ItemStack map = plugin.getPassMap(args[2]);
                    if (map == null) {
                        sendFailedMessage(sender, "そのPassMap名は存在しません...");
                        return true;
                    }
                    Map<CommandSender, String> deletes = plugin.getDeletes();
                    if (deletes.containsKey(sender) && deletes.get(sender).equals(args[2])) {
                        plugin.getPassMaps().remove(map);
                        deletes.remove(sender);
                        for (PassMap pass : plugin.getPasses()) {
                            if (!pass.getKey().equals(args[2])) continue;
                            plugin.getRemoves().add(pass);
                        }
                        sendSuccessMessage(sender, "削除しました！");
                        return true;
                    }
                    deletes.put(sender, args[2]);
                    sendWarningMessage(sender, "本当に削除しますか？");
                    sendWarningMessage(sender, "削除すると額縁の保護が解除されます");
                    sendAdviceMessage(sender, "それでも削除する場合はもう一度同じコマンドを実行して見てください");
                    return true;
                }
                if (canExecuteCommand(args[1], "list", sender)) {
                    int page = 1;
                    if (args.length == 3) {
                        if (!NumberUtils.isNumber(args[2])) {
                            sendFailedMessage(sender, "開きたいページの数値を入力してください");
                            return true;
                        }
                        page = Integer.parseInt(args[2]);
                    }
                    sendPage(sender, plugin.getPassMaps(), 15, page, item -> {
                        MapMeta meta = (MapMeta) item.getItemMeta();
                        MapView view = meta.getMapView();
                        if (view == null) return null;

                        TextComponent text = (TextComponent) meta.displayName();
                        String name = plugin.getKey(item);
                        if (text != null) name = text.content() + "§7(" + name + ")";
                        long disCount = plugin.getPasses().stream()
                                .filter(pass -> pass.getKey().equals(plugin.getDisplayName(item))).count();
                        long keyCount = plugin.getPasses().stream().filter(pass -> {
                            String key = plugin.getKey(pass.getFrame().getItem());
                            if (key == null) return false;
                            return key.equals(plugin.getKey(item));
                        }).count();

                        return view.getId() + " §7: " + name + "§f x§6" + disCount + "§7(" + keyCount + ")";
                    });
                    return true;
                }
            }
            sendErrorMessage(sender, "mlock", "そのコマンドは無いみたいです...");
            return true;
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            そのコマンドを実行する為に必要な条件を全て満たしているかを確認します。
    public boolean canExecuteCommand(String argument, String command, CommandSender sender) {
        if (!Man10LockettePass.equals(argument, command)) return false;
        return sender.hasPermission("mlockette.command." + command);
    }

    ///////////////////////////////////////////////////////////////////////////
    //            コマンドが分からない時に送るメッセージです。
    public void sendCommandHelp(CommandSender sender) {
        sendSuccessMessage(sender, "Man10LockettePass Plugin §bHelp");
        sendInfoMessage(sender, "mlock help - コマンドを確認できます");
        sendInfoMessage(sender, "mlock list (page) - PassMap一覧が見れます");
        sendInfoMessage(sender, "mlock set <name> [Password] - PassWordが設定できます");
        if (sender.hasPermission("mlockette.command.op.*")) {
            sendAdviceMessage(sender, "mlock get <name> - PassMapを取得できます");
            sendAdviceMessage(sender, "mlock reload - 再読み込みします");
            sendAdviceMessage(sender, "mlock passmap create <name> [DisplayName] (lore...) - PassMapを作成します");
            sendAdviceMessage(sender, "mlock passmap delete <name> - PassMapを削除します");
            sendAdviceMessage(sender, "mlock passmap list (page) - 作成されたPassMapが見れます");
        }
        if (isJoke) sendAdviceMessage(sender, "今日は閏年です！張り切って生きましょう！");
    }

    ///////////////////////////////////////////////////////////////////////////
    //            アイテムをgiveコマンド風に渡します。
    public static void giveItem(CommandSender sender, Player target, String name, ItemStack item) {
        String jp = "§f" + target.getName() + " に §f§o[" + name + "§f§o]§f を 1 個与えました";
        String en = "§fGive 1 [§f" + name + "§f] to §f" + target.getName();
        String text = "§7§o[mlockette:" + sender.getName() + "§7§o: ＠Ｍａｎ１０＠§7§o]";

        if (sender instanceof Player) {
            sender.sendMessage(jp);
            Bukkit.getLogger().info(text.replace("＠Ｍａｎ１０＠", en.replace("§f", "§7§o")));
        } else Bukkit.getLogger().info(en);
        target.getInventory().addItem(item);

        for (OfflinePlayer offlinePlayer : Bukkit.getOperators()) {
            Player player = offlinePlayer.getPlayer();
            if (player == null || player.getName().equals(target.getName())) {
                continue;
            }

            jp = jp.replace("[" + name + "]", "§f§o[" + name + "§f§o]");
            player.sendMessage(text.replace("＠Ｍａｎ１０＠", jp.replaceAll("§f(^§o)", "§7§o\1")));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //            パスワードを設定します。
    public void setPassword(CommandSender sender, PassMap pass, String password) {
        if (password.length() >= String.valueOf(Integer.MAX_VALUE).length() - 1) {
            sendFailedMessage(sender, "パスワードが長すぎます...");
            return;
        }
        if (!NumberUtils.isNumber(password)) {
            sendFailedMessage(sender, "パスワードには数値を入力してください...");
            return;
        }
        pass.setPassword(NumberUtils.toInt(password, Integer.MAX_VALUE));
        sendSuccessMessage(sender, pass.getKey() + " のパスワードを " + pass.getPassword() + " に変更できました！");
        sendAdviceMessage(sender, "忘れても再設定すれば大丈夫です！");
    }

    // list が 23 size が 10 page が 2
    // pageMax は 3
    public static <E> void sendPage(CommandSender sender, List<? extends E> list, int size, int page, Function<E, String> function) {
        // 情報の部分です。
        if (list.isEmpty()) {
            sendSuccessMessage(sender, "PassMapの情報です！");
            sendInfoMessage(sender, "すっからかんみたいです...");
            return;
        }
        int pageMax = (list.size() / size) + (list.size() % size != 0 ? 1 : 0);
        if (1 > page || page > pageMax) {
            sendFailedMessage(sender, "1 〜 " + pageMax + " で指定してください");
            return;
        }
        sendSuccessMessage(sender, "PassMapの情報です！");

        int min = size * (page - 1);
        int max = Math.min(min + size, list.size());
        list.subList(min, max).stream().map(function)
                .filter(Objects::nonNull).forEach(text -> sendInfoMessage(sender, text));

        // ページの部分です。
        Component back = Component.text( "§6[" + (page - 1) + " Page] <- ")
                .clickEvent(ClickEvent.runCommand("/mlock passmap list " + (page - 1)));
        Component text = Component.text("§f[" + page + " Page]");
        Component next = Component.text("§6 -> [" + (page + 1) + " Page]")
                .clickEvent(ClickEvent.runCommand("/mlock passmap list " + (page + 1)));

        if (1 == page) text = Component.text( "§7[" + 0 + " Page] <- ").append(text);
        else text = back.append(text);
        if (pageMax == page) text = text.append(Component.text("§7 -> [" + (page + 1) + " Page]"));
        else text = text.append(next);

        sender.sendMessage(Component.text(SendMessage.PREFIX).append(text));
    }
}

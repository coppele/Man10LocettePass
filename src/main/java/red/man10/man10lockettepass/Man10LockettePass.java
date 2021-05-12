package red.man10.man10lockettepass;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Man10LockettePass extends JavaPlugin {

    private List<ItemStack> passMaps; // [登録]されているマップを管理します。
    private List<PassMap> passes; // サーバーに設置されているマップを管理します。
    private Map<CommandSender, String[]> creates; // /mlock passmap create を実行して被りがあった場合に実行者が追加されます。
    private Map<Player, PassMap> selects; // PassMapをシフトクリックした際に追加されます
    private List<PassMap> removes; // ドロップできるPassMapが追加されます。
    private Map<CommandSender, String> deletes; // /mlock passmap delete を実行して被りがあった場合に実行者が追加されます。

    // 画像　　       ：MappRenderer.imageMap.keySet()
    // 作成したPassMap：passMaps
    // 設置したPassMap：passes
    // 選択したPassMap：selects
    // 破壊したPassMap：removes
    // ややこしいですね(´･ω･`)

    ///////////////////////////////////////////////////////////////////////////
    //            passMapsを返します。
    public List<ItemStack> getPassMaps() {
        return passMaps;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            passMapsをStringにして返します。
    public List<String> getPassMapsToDisplay() {
        return passMaps.stream().map(this::getDisplayName).collect(Collectors.toList());
    }
    public List<String> getPassMapsToDisplay(char replaceSection) {
        return getPassMapsToDisplay().stream()
                .map(text -> text.replace('§', replaceSection))
                .collect(Collectors.toList());
    }
    public List<String> getPassMapsToKey() {
        return passMaps.stream().map(this::getKey).filter(Objects::nonNull).collect(Collectors.toList());
    }
    ///////////////////////////////////////////////////////////////////////////
    //            passMapを登録します。
    public void registerPassMap(String name, String display, List<String> lore) {
        ItemStack item = getMapItem(name);
        if (item == null) return;
        MapMeta meta = (MapMeta) item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        display = ChatColor.translateAlternateColorCodes('&', display);
        meta.displayName(Component.text("§6" + display));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(text -> Component.text("§7" + ChatColor.translateAlternateColorCodes('&', text))).collect(Collectors.toList()));
        }
        data.set(new NamespacedKey(this, "Key"), PersistentDataType.STRING, name);
        data.set(new NamespacedKey(this, "Display"), PersistentDataType.STRING, display);

        item.setItemMeta(meta);
        passMaps.add(item);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            passMapを返します。
    public ItemStack getPassMap(@NotNull String name) {
        for (ItemStack map : passMaps) {
            String display = getDisplayName(map);
            if (display == null) continue;
            display = ChatColor.translateAlternateColorCodes('&', display);
            if (display.equals(ChatColor.translateAlternateColorCodes('&', name))) {
                return map;
            }
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            passesを返します。
    public List<PassMap> getPasses() {
        return passes;
    }
    public List<PassMap> getPasses(CommandSender sender) {
        if (sender.isOp() || !(sender instanceof Player)) return passes;
        Player player = (Player) sender;
        return passes.stream().filter(pass -> pass.getOwner().equals(player.getUniqueId()))
                .collect(Collectors.toList());
    }
    public List<PassMap> getPasses(Block block) {
        return passes.stream().filter(pass -> getBehindBlock(pass.getFrame()).getLocation().equals(block.getLocation()))
                .collect(Collectors.toList());
    }
    ///////////////////////////////////////////////////////////////////////////
    //            passMapを返します。
    @Nullable
    public PassMap getPass(UUID uuid) {
        for (PassMap pass : passes) {
            if (!pass.getUUID().equals(uuid)) continue;
            return pass;
        }
        return null;
    }
    @Nullable
    public PassMap getPass(ItemFrame frame) {
        for (PassMap pass : passes) {
            if (!pass.getFrame().equals(frame)) continue;
            return pass;
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            createsを返します。
    public Map<CommandSender, String[]> getCreates() {
        return creates;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            selectsを返します。
    public Map<Player, PassMap> getSelects() {
        return selects;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            removesを返します。
    public List<PassMap> getRemoves() {
        return removes;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            deletesを返します。
    public Map<CommandSender, String> getDeletes() {
        return deletes;
    }


    ///////////////////////////////////////////////////////////////////////////
    // 初期化関連です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onEnable() {
        SendMessage.setPrefix("§6[mLockette]§r "); // 接頭辞です。
        reload();
        setUpEvent();
    }

    public void reload() {
        passMaps = new ArrayList<>();
        passes = new ArrayList<>();
        // TODO:この二つについてはsqlから取得するロジックを組んでおきます

        creates = new HashMap<>();
        selects = new HashMap<>();
        removes = new ArrayList<>();
        deletes = new HashMap<>();

        setUpCommand();
        setUpImages();
    }

    ///////////////////////////////////////////////////////////////////////////
    //            コマンドの設定を行います。
    private void setUpCommand() {
        PluginCommand command = getCommand("mlock");
        if (command == null) return;

        command.setExecutor(new Command(this, isJoke()));
        command.setTabCompleter(new TabComplete(this));
    }
    ///////////////////////////////////////////////////////////////////////////
    //            イベントの設定を行います。
    private void setUpEvent() {
        getServer().getPluginManager().registerEvents(new Event(this), this);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            閏年かどうかを確認します。
    public boolean isJoke() {
        Calendar now = Calendar.getInstance();
        Calendar leapYear = Calendar.getInstance();
        leapYear.set(leapYear.get(Calendar.YEAR), Calendar.FEBRUARY, 29);
        return now.equals(leapYear);
    }

    ///////////////////////////////////////////////////////////////////////////
    //            MappRenderer関連の設定を行います。
    private void setUpImages() {
        File file = new File(getDataFolder(), "PassMaps");
        MappRenderer.setup(this);
        for (File image : file.listFiles()) {
            String[] split = image.getName().split("\\.");
            if (!split[split.length - 1].equals("png")) continue;
            drawImage(split[0]);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // その他です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //            コマンドが要求コマンドと一致しているかを大文字小文字関係なく比較します。
    public static boolean equals(String argument, String command) {
        return argument.toLowerCase(Locale.ROOT).equals(command);
    }

    ///////////////////////////////////////////////////////////////////////////
    //            コマンドが要求コマンドと一致しているかを大文字小文字関係なく比較します。
    public static boolean matchUUID(String uuid) {
        return uuid.matches("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$");
    }

    ///////////////////////////////////////////////////////////////////////////
    //            描画します。
    private void drawImage(String imageKey) {
        MappRenderer.draw(imageKey, 0, (key, mapId, g) -> {
            BufferedImage image = MappRenderer.image(imageKey);
            if (image == null) {
                getLogger().warning(imageKey + ".png が無いみたいです(´･ω･`)");
                g.drawString("No Image Found", 10, 10);
                return false;
            }
            g.drawImage(image, 0, 0, 128, 128, null);
            return true;
        });
        MappRenderer.displayTouchEvent(imageKey, (key, mapId, player, x, y) -> true);
    }

    ///////////////////////////////////////////////////////////////////////////
    //            ItemStackからキーを取得します
    @Nullable
    public String getKey(ItemStack map) {
        PersistentDataContainer data = map.getItemMeta().getPersistentDataContainer();
        return data.get(new NamespacedKey(this, "Key"), PersistentDataType.STRING);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            ItemStackから表示名を取得します
    @Nullable
    public String getDisplayName(ItemStack map) {
        PersistentDataContainer data = map.getItemMeta().getPersistentDataContainer();
        return data.get(new NamespacedKey(this, "Display"), PersistentDataType.STRING);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            mapIDに同じものがあればそれを、なければ作成します。
    public ItemStack getMapItem(String key) {
        Map<Integer, String> maps = new TreeMap<>();
        getConfig().getStringList("Maps").forEach(map -> {
            if (!map.matches("^\\d+,\\w+$")) return;
            String[] split = map.split(",");
            if (maps.containsValue(split[1])) return;
            maps.put(Integer.parseInt(split[0]), split[1]);
        });
        if (!maps.isEmpty()) for (Map.Entry<Integer, String> map : maps.entrySet()) {
            if (map.getValue().equals(key)) {
                ItemStack item = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) item.getItemMeta();
                meta.setMapView(getServer().getMap(map.getKey()));
                item.setItemMeta(meta);
                return item;
            }
        }
        return MappRenderer.createMapItem(this, key);
    }

    ///////////////////////////////////////////////////////////////////////////
    //            額縁の背後にあるブロックを取得します。
    public static Block getBehindBlock(ItemFrame frame) {
        return frame.getLocation().getBlock().getRelative(frame.getFacing().getOppositeFace());
    }
}
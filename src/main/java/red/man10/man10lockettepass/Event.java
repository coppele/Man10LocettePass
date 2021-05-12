package red.man10.man10lockettepass;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static red.man10.man10lockettepass.SendMessage.sendFailedMessage;
import static red.man10.man10lockettepass.SendMessage.sendHoverClickMessage;
import static red.man10.man10lockettepass.SendMessage.sendSuccessMessage;

///////////////////////////////////////////////////////////////////////////
// イベント関連です(´･ω･`)
///////////////////////////////////////////////////////////////////////////

public final class Event implements Listener {

    private final Man10LockettePass plugin;
    public Event(Man10LockettePass plugin) {
        this.plugin = plugin;
    }

    ///////////////////////////////////////////////////////////////////////////
    // 設置と選択関連です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    private void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) return;
        ItemFrame frame = (ItemFrame) event.getRightClicked();
        Player player = event.getPlayer();

        if (placePassMap(frame, player)) return; // PassMap を設置してみます(´･ω･`)
        if (selectPassMap(frame, player)) return; // PassMap を選択してみます(´･ω･`)
        typingPassMap(frame, player, event.getClickedPosition()); // PassMap で入力してみます(´･ω･`)
    }
    ///////////////////////////////////////////////////////////////////////////
    //            PassMapを設置してみます。
    private boolean placePassMap(ItemFrame frame, Player player) {
        ItemStack map = player.getInventory().getItemInMainHand();
        if (map.getType() != Material.FILLED_MAP) return false;
        String name = plugin.getDisplayName(map);
        if (name == null) return false;
        if (plugin.getPassMap(name) == null) return false;
        if (plugin.getPass(frame) != null) return false;
        PassMap pass = new PassMap(name, frame, player);
        pass.getDoors().addAll(getNearbyDoor(Man10LockettePass.getBehindBlock(frame)));

        plugin.getPasses().add(pass);
        String owner = plugin.getServer().getOfflinePlayer(pass.getOwner()).getName();
        Location loc = pass.getFrame().getLocation();
        sendHoverClickMessage(player, "§a設置しました！",
                "§7Owner: " + owner + "\n" +
                        "§7UUID: " + pass.getUUID() + "\n" +
                        "§7Location: " + loc.getWorld().getName() + "," + loc.toBlockLocation().toVector(),
                "/tell " + owner + " ");
        sendHoverClickMessage(player,
                "§aこのメッセージをクリックしてパスワードを設定してください！",
                "§6§n[パスワードを設定する]",
                "/mlock set " + pass.getUUID() + " ");
        return true;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            PassMapを選択してみます。
    private boolean selectPassMap(ItemFrame frame, Player player) {
        if (!player.isSneaking()) return false;

        PassMap pass = plugin.getPass(frame);
        if (pass == null) return false;
        plugin.getSelects().put(player, pass);
        String owner = plugin.getServer().getOfflinePlayer(pass.getOwner()).getName();
        Location loc = pass.getFrame().getLocation();
        sendHoverClickMessage(player, "§a選択しました！",
                "§7Owner: " + owner + "\n" +
                        "§7UUID: " + pass.getUUID() + "\n" +
                        "§7Location: " + loc.getWorld().getName() + "," + loc.toBlockLocation().toVector(),
                "/tell " + owner + " ");
        sendHoverClickMessage(player,
                "§aこのメッセージをクリックしてパスワードを設定してください！",
                "§6§n[パスワードを設定する]",
                "/mlock set ");
        return true;
    }
    ///////////////////////////////////////////////////////////////////////////
    //            PassMapで入力してみます。
    private void typingPassMap(ItemFrame frame, Player player, Vector click) {
        ItemStack map = frame.getItem();
        if (map.getType() != Material.FILLED_MAP) return;
        PassMap pass = plugin.getPass(frame);
        if (pass == null) return;
        if (pass.getPassword() == -1) {
            sendFailedMessage(player, "このPassMapはPasswordが未設定です...");
            return;
        }
        Vector vec = toCoordinate(click, frame.getFacing());
        if (Math.abs(vec.getX()) == 4 || Math.abs(vec.getY()) == 4) return;
        PassMap.KeyBoard key = PassMap.KeyBoard.getKey(vec);
        if (key == null) return;
        ItemMeta meta = map.getItemMeta();
        String display = "§6" + plugin.getDisplayName(map);

        if (key == PassMap.KeyBoard.ENTER) {
            if (pass.getPassword() == pass.getPutPassword()) {
                openDoors(pass);
            } else {
                sendFailedMessage(player, "そのパスワードは間違っています...");
            }
            meta.displayName(Component.text(display));
            pass.clearPutPassword();
        } else {
            String putPass = String.valueOf(pass.getPutPassword());
            if (key != PassMap.KeyBoard.DELETE && putPass.length() == String.valueOf(Integer.MAX_VALUE).length() - 1) {
                sendFailedMessage(player, "それ以上は入力出来ません...");
                return;
            }
            pass.typePassword(key);
            putPass = String.valueOf(pass.getPutPassword()).replaceAll("\\d", "*");
            if (0 >= pass.getPutPassword()) meta.displayName(Component.text(display));
            else meta.displayName(Component.text("§7" + putPass));
        }
        map.setItemMeta(meta);
        frame.setItem(map, false);
    }

    ///////////////////////////////////////////////////////////////////////////
    // 撤去関連です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    private void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) return;
        ItemFrame frame = (ItemFrame) event.getEntity();
        ItemStack map = frame.getItem();
        if (map.getType() != Material.FILLED_MAP) return;
        if (!(event.getDamager() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getDamager();
        PassMap pass = plugin.getPass(frame);
        if (pass == null) return;
        if (!pass.getOwner().equals(player.getUniqueId()) && !player.isOp()) {
            event.setCancelled(true);
            sendFailedMessage(player, "あなたは設置した人ではないため撤去できません！");
            return;
        }
        ItemMeta meta = map.getItemMeta();
        meta.displayName(Component.text(plugin.getDisplayName(map) + ""));
        map.setItemMeta(meta);
        frame.setItem(map);

        plugin.getPasses().remove(pass);
        plugin.getSelects().remove(player);
        sendSuccessMessage(player, "撤去しました！");
    }

    @EventHandler(ignoreCancelled = true)
    private void onQuit(PlayerQuitEvent event) {
        plugin.getCreates().remove(event.getPlayer());
        plugin.getSelects().remove(event.getPlayer());
        plugin.getDeletes().remove(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    private void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Block door = getDoor(block);
        if (door != null) {
            for (PassMap pass : getNearbyPassMap(door.getLocation(), 1.7)) {
                if (!pass.getDoors().contains(door)) continue;
                if (!pass.getOwner().equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    sendFailedMessage(player, "あなたは設置した人ではないため撤去できません！");
                    return;
                }
                pass.getDoors().remove(door);
            }
        }

        Block up = block.getRelative(BlockFace.UP);
        if (block.getType() != Material.IRON_DOOR && up.getType() == Material.IRON_DOOR) {
            for (PassMap pass : plugin.getPasses()) {
                if (!pass.getDoors().contains(up)) continue;
                if (!pass.getOwner().equals(player.getUniqueId()) && !player.isOp()) {
                    event.setCancelled(true);
                    sendFailedMessage(player, "あなたは設置した人ではないため撤去できません！");
                    return;
                }
                pass.getDoors().remove(up);
            }
        }

        List<PassMap> passes = new ArrayList<>();
        for (PassMap pass : plugin.getPasses(block)) {
            if (!pass.getOwner().equals(player.getUniqueId()) && !player.isOp()) {
                event.setCancelled(true);
                sendFailedMessage(player, "あなたは設置した人ではないため撤去できません！");
                return;
            }
            passes.add(pass);
        }

        plugin.getRemoves().addAll(passes); // 破壊したブロックにくっついていたPassMap が帰ってきます
    }

    @EventHandler(ignoreCancelled = true)
    private void onBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) return;
        ItemFrame frame = (ItemFrame) event.getEntity();
        ItemStack item = frame.getItem();
        if (item.getType() != Material.FILLED_MAP) return;
        String name = plugin.getDisplayName(item);
        if (name == null) return;
        if (plugin.getPassMap(name) == null) return;
        PassMap pass = plugin.getPass(frame);
        if (pass == null) return;

        if (plugin.getRemoves().contains(pass)) {
            plugin.getPasses().remove(pass);
            plugin.getRemoves().remove(pass);
            Player player = plugin.getServer().getPlayer(pass.getOwner());
            if (player == null) return;
            sendSuccessMessage(player, "撤去しました！");

        } else event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    private void onBuild(BlockPlaceEvent event) {
        if (!event.canBuild()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();

        List<PassMap> passes = new ArrayList<>();
        for (PassMap pass : getNearbyPassMap(block.getLocation(), 0.5)) {
            if (!pass.getOwner().equals(player.getUniqueId()) && !player.isOp()) {
                event.setBuild(false);
                event.setCancelled(true);
                sendFailedMessage(player, "あなたは設置した人ではないため建設できません！");
                return;
            }
            passes.add(pass);
        }
        plugin.getRemoves().addAll(passes);
    }
    @EventHandler(ignoreCancelled = true)
    private void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (!plugin.getPasses(block).isEmpty()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // 赤石系です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler
    private void onBlock(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        for (int i = 0; i < 6; i++) {
            Block door = getDoor(block.getRelative(BlockFace.values()[i]));
            if (door == null) continue;
            for (PassMap pass : plugin.getPasses()) {
                if (!pass.getDoors().contains(door)) continue;

                event.setNewCurrent(0);
                ctrlDoor(door, false, false);
                Block mirror = getMirror(door);
                if (mirror != null) ctrlDoor(mirror, false, false);
                return;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // その他です(´･ω･`)
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    //            BlockFaceからVectorの数値を整えます
    //    y
    // -x + x
    //   -y　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　_φ(´･ω･` )
    // ,-------v--------v--------v--------、 ,-------v--------v--------v--------、
    // | x,y,z | 1, 0, 0| 0, 1, 0| 1, 1, 0|　| x,y,z |-1, 0, 0| 0,-1, 0|-1,-1, 0|
    // |-------+--------+--------+--------|　|-------+--------+--------+--------|
    // | north |-1, 0, 0| 0, 1, 0|-1, 1, 0|　| north | 1, 0, 0| 0,-1, 0| 1,-1, 0|
    // | east  | 0, 0,-1| 0, 1, 0| 0, 1,-1|　| east  | 0, 0, 1| 0,-1, 0| 0,-1, 1|
    // | south | 1, 0, 0| 0, 1, 0| 1, 1, 0|　| south |-1, 0, 0| 0,-1, 0|-1,-1, 0|
    // | west  | 0, 0, 1| 0, 1, 0| 0, 1, 1|　| west  | 0, 0,-1| 0,-1, 0| 0,-1,-1|
    // | up    | 1, 0, 0| 0, 0,-1| 1, 0,-1|　| up    |-1, 0, 0| 0, 0, 1|-1, 0, 1|
    // | down  | 1, 0, 0| 0, 0, 1| 1, 0, 1|　| down  |-1, 0, 0| 0, 0,-1|-1, 0,-1|
    // `-------^--------^--------^--------’　`-------^--------^--------^--------’
    public Vector toCoordinate(Vector vector, BlockFace facing) {
        Vector vec = vector.clone().multiply(10);
        long x = Math.round(vec.getX());
        long y = Math.round(vec.getY());
        long z = Math.round(vec.getZ());
        if (facing == BlockFace.NORTH) return new Vector(-x, y, 0);
        if (facing == BlockFace.EAST ) return new Vector(-z, y, 0);
        if (facing == BlockFace.SOUTH) return new Vector( x, y, 0);
        if (facing == BlockFace.WEST ) return new Vector( z, y, 0);
        if (facing == BlockFace.UP   ) return new Vector( x,-z, 0);
        if (facing == BlockFace.DOWN ) return new Vector( x, z, 0);
        return new Vector();
    }
    public Block getDoor(Block block) {
        if (block.getType() != Material.IRON_DOOR) return null;
        Door door = (Door) block.getBlockData();
        if (door.getHalf() == Bisected.Half.BOTTOM) return block;
        return block.getRelative(BlockFace.DOWN);
    }
    public Block getMirror(Block block) {
        Door door = (Door) block.getBlockData();
        for (int i = 0; i < 4; i++) {
            Block target = getDoor(block.getRelative(BlockFace.values()[i]));
            if (target == null) continue;
            Door mirror = (Door) target.getBlockData();
            if (door.getFacing() != mirror.getFacing()) continue;
            if (door.getHinge() == mirror.getHinge()) continue;
            return target;
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            PassMapの周りにあるドアを取得します
    public List<Block> getNearbyDoor(Block block) {
        List<Block> doors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Block target = getDoor(block.getRelative(BlockFace.values()[i]));
            if (target == null) continue;
            doors.add(target);
            Block mirror = getMirror(target);
            if (mirror == null) continue;
            doors.add(mirror);
        }
        return doors;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            ドアを開閉します。
    public void ctrlDoors(PassMap pass, boolean open, boolean playSound) {
        for (Block block : pass.getDoors()) {
            ctrlDoor(block, open, playSound);
        }
    }
    public void ctrlDoor(Block block, boolean open, boolean playSound) {
        Sound sound = open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
        BlockState state = block.getState();
        Door door = (Door) block.getBlockData();
        door.setOpen(open);
        state.setBlockData(door);
        state.update();
        if (!playSound) return;
        block.getWorld().playSound(block.getLocation(), sound, SoundCategory.BLOCKS, 1, 1);
    }
    ///////////////////////////////////////////////////////////////////////////
    //            ドアを開閉します。
    public void openDoors(PassMap pass) {
        ctrlDoors(pass, true, true);
        new BukkitRunnable() {
            @Override
            public void run() {
                ctrlDoors(pass, false, true);
            }
        }.runTaskLater(plugin, 40L);
    }

    private List<PassMap> getNearbyPassMap(Location location, double radius) {
        return location.getWorld().getNearbyEntitiesByType(ItemFrame.class, location.toCenterLocation(), radius).stream()
                .map(plugin::getPass).filter(Objects::nonNull).collect(Collectors.toList());
    }
}

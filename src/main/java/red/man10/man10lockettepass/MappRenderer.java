package red.man10.man10lockettepass;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import static java.lang.Math.sqrt;
import static org.bukkit.Bukkit.getServer;

//////////////////////////////////////////////////////////
//     MappRenderer
//                             created by takatronix.com
//
//     https://github.com/takatronix/MappStore/
//      MIT Licence
//////////////////////////////////////////////////////////
// ちょこっとeditしました -cp

//////////////////////////////////////////////////////////
//    (1)      Setup
//    プラグインのonEnable()で　MappRenderer.setup(this)
//
//      pluginsFolder/PassMaps/
//      の下に画像をおくと、自動読み込みされます
//      0000.png => key: "0000"
//
//      (2) onEnable()などで描画関数登録


public class MappRenderer extends MapRenderer implements Listener {


    //////////////////////////////////////////////
    //      Singleton
    private static final MappRenderer sharedInstance = new MappRenderer();
    private MappRenderer() {
        log("MappRenderer created..");
    }
    public static MappRenderer getInstance() {
        return sharedInstance;
    }

    static int getMapId(ItemStack map) {

        // return (int)map.getDurability();
        if (map.getType().equals(Material.FILLED_MAP) && map.hasItemMeta()) {
            MapMeta meta = (MapMeta) map.getItemMeta();
            return meta.getMapView().getId();
        }

        return 0;
    }
    static void setMapId(ItemStack item, int mapId) {
        item.setDurability((short) mapId);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapId(mapId);
        item.setItemMeta(meta);
    }

    static void log(String text) {
        if (debugMode) Bukkit.getLogger().info(text);
    }


    ///////////////////////////////////////////////
    //      描画関数インタフェース
    @FunctionalInterface
    public interface DrawFunction {
        boolean draw(String key, int mapId, Graphics2D g);
    }
    @FunctionalInterface
    public interface InitFunction {
        boolean onInit(String key, int mapId);
    }

    //      ボタンクリックイベント
    @FunctionalInterface
    public interface ButtonClickFunction {
        boolean onButtonClicked(String key, int mapId, Player player);
    }
    //      ボタンクリックイベント
    @FunctionalInterface
    public interface PlatePushFunction {
        boolean onPlatePush(String key, int mapId, Player player);
    }


    //      ジャンプイベント（プレイヤーがマップを持ってジャンプした)
    @FunctionalInterface
    public interface PlayerJumpFunction {
        boolean onPlayerJumped(String key, int mapId, Player player);
    }

    //      スニークイベント（プレイヤーがマップを持ってジャンプした)
    @FunctionalInterface
    public interface PlayerSneakFunction {
        boolean onPlayerSneaked(String key, int mapId, Player player, boolean isSneaking);
    }

    //      プレイヤーの向きが変更
    @FunctionalInterface
    public interface PlayerYawFunction {
        boolean onPlayerYawChanged(String key, int mapId, Player player, double angle, double velocity);
    }

    @FunctionalInterface
    public interface PlayerPitchFunction {
        boolean onPlayerPitchChanged(String key, int mapId, Player player, double angle, double velocity);
    }
    @FunctionalInterface
    public interface PlayerChatFunction {
        boolean onPlayerChat(String key, int mapId, AsyncPlayerChatEvent event);
    }


    //     画面タッチ
    @FunctionalInterface
    public interface DisplayTouchFunction {
        boolean onDisplayTouch(String key, int mapId, Player player, int x, int y);
    }
    @EventHandler
    public void onItemInteract(PlayerInteractEntityEvent event) {
        //           回転抑制用
        MappRenderer.onPlayerInteractEntityEvent(event);
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        //      イベントを通知してやる（ボタン検出用)
        MappRenderer.onPlayerInteractEvent(e);
    }


    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {

        //      プレイヤーがマップを持っていなければ抜け　
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.FILLED_MAP) {
            return;
        }

        int mapID = getMapId(item);

        String key = findKey(mapID);
        if (key == null) {
            return;
        }
        PlayerSneakFunction func = sneakFunctions.get(key);
        if (func != null) {
            if (func.onPlayerSneaked(key, mapID, player, player.isSneaking())) {
                refresh(key);
            }
        }

    }


    //      マウスカーソル情報
    static class Cursor {
        int x;
        int y;
        boolean show;
    }
    static HashMap<Integer, Cursor> mouseCursor = new HashMap<>();

    static HashMap<Player, Vector> userMovingVec = new HashMap<>();

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        //      プレイヤーがマップを持っていなければ抜け　
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.FILLED_MAP) {
            return;
        }

        int mapID = getMapId(item);


        Vector lastMovingVec = userMovingVec.get(player);
        Vector movingVec = e.getFrom().toVector().subtract(e.getTo().toVector());
        userMovingVec.put(player, movingVec);



        if (lastMovingVec == null) {
            return;
        }

        ////////////////////////////////////
        //      ジャンプした瞬間
        ////////////////////////////////////
        if (lastMovingVec.getY() == 0 && movingVec.getY() < 0) {

            String key = findKey(mapID);
            if (key == null) {
                return;
            }
            //      ジャンプイベントを通知
            PlayerJumpFunction func = jumpFunctions.get(key);
            if (func != null) {
                if (func.onPlayerJumped(key, mapID, player)) {
                    refresh(key);
                }
            }
        }

    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {


        /*
        ||      プレイヤーがマップを持っていなければ抜け　
        || Player player = event.getPlayer();
        ||
        || ItemStack item = player.getInventory().getItemInMainHand();
        || if (item.getType() != Material.FILLED_MAP) {
        ||     return;
        || }
        || int mapID = (int)item.getDurability();
        || String key = findKey(mapID);
        || if (key == null) {
        ||     return;
        || }
        */

        for(MappRenderer r : renderers) {
            PlayerChatFunction func = chatFunctions.get(r.key);
            if (func != null) {
                log("event chat");
                if (func.onPlayerChat(r.key, r.mapId, event)) {
                    refresh(key);
                }
            }
        }
    }



    ///////////////////////////////////////////////
    //      "key" ->　関数　をハッシュマップに保存
    static HashMap<String, DrawFunction> drawFunctions = new HashMap<>();
    static HashMap<String, Integer> drawRefreshTimeMap = new HashMap<>();


    //      初期化関数登録
    static HashMap<String, InitFunction> initFunctions = new HashMap<>();
    public static void init(String key, InitFunction func) {
        initFunctions.put(key, func);
    }


    //
    static HashMap<String, ButtonClickFunction> buttonFunctions = new HashMap<>();
    static HashMap<String, DisplayTouchFunction> touchFunctions = new HashMap<>();

    //        描画検索用
    static ArrayList<MappRenderer> renderers = new ArrayList<>();
    //      描画関数をキーを登録
    //      key: キー func: 描画関数 refreshIntervalTick:自動更新周期(1tick=1/20秒) 0で自動更新しない
    public static void draw(String key, int refreshIntervalTick, DrawFunction func) {
        drawRefreshTimeMap.put(key, refreshIntervalTick);
        drawFunctions.put(key, func);
    }
    //     ボタンクリックイベントを追加
    public static void buttonEvent(String key, ButtonClickFunction func) {
        buttonFunctions.put(key, func);
    }

    //    plateイベントを追加
    static HashMap<String, PlatePushFunction> plateFunctions = new HashMap<>();
    public static void plateEvent(String key, PlatePushFunction func) {
        plateFunctions.put(key, func);
    }


    //     タッチイベントを追加
    public static void displayTouchEvent(String key, DisplayTouchFunction func) {
        touchFunctions.put(key, func);
    }

    //    PlayerJumpイベントを追加
    static HashMap<String, PlayerJumpFunction> jumpFunctions = new HashMap<>();
    public static void playerJumpEvent(String key, PlayerJumpFunction func) {
        jumpFunctions.put(key, func);
    }
    //    PlayerSneakイベントを追加
    static HashMap<String, PlayerSneakFunction> sneakFunctions = new HashMap<>();
    public static void playerSneakEvent(String key, PlayerSneakFunction func) {
        sneakFunctions.put(key, func);
    }
    //    Directionイベントを追加
    static HashMap<String, PlayerPitchFunction> pitchFunctions = new HashMap<>();
    public static void playerPitchEvent(String key, PlayerPitchFunction func) {
        pitchFunctions.put(key, func);
    }

    static HashMap<String, PlayerYawFunction> yawFunctions = new HashMap<>();
    public static void playerYawEvent(String key, PlayerYawFunction func) {
        yawFunctions.put(key, func);
    }

    //    chatイベントを追加
    static HashMap<String, PlayerChatFunction> chatFunctions = new HashMap<>();
    public static void playerChatEvent(String key, PlayerChatFunction func) {
        chatFunctions.put(key, func);
    }



    //     キー
    String key = null;
    int    mapId = -1;
    //   オフスクリーンバッファを作成する
    //   高速化のためこのバッファに描画し、マップへ転送する
    BufferedImage bufferedImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

    //      画面リフレッシュサイクル:tick = 1/20秒
    public int refreshInterval = 0;
    //      一度だけ更新する
    public boolean refreshOnce = false;
    //      マップへ転送する
    public boolean updateMapOnce = false;
    //      描画時間
    public long drawingTime = 0;
    //      描画した回数
    public int updateCount = 0;
    //      bukkitからrenderコールされた回数
    public int renderCount = 0;
    //      デバッグ表示フラグ
    public static boolean debugMode = false;


    //////////////////////////////////////
    //      描画関数&速度測定
    //////////////////////////////////////
    void draw() {
        //      関数をキーで取り出し実行
        DrawFunction func = drawFunctions.get(key);
        if (func != null) {
            long startTime = System.nanoTime();
            //      描画関数をコール
            if (func.draw(key, mapId, bufferedImage.createGraphics())) {
                updateMapOnce = true;
            }
            drawingTime = System.nanoTime() - startTime;
            //log("drawTime:"+key + ":"+drawingTime);
        }
    }


    int tickRefresh = 0;



    /////////////////////////////////
    //      Tickイベント
    //      描画更新があれば反映
    public void onTick() {
        if (refreshOnce) {
            refreshOnce = false;
            draw();
        }

        refreshInterval = drawRefreshTimeMap.getOrDefault(key, 0);
        if (refreshInterval == 0) {
            return;
        }
        tickRefresh++;
        //      インターバル期間をこえていたら画面更新
        if (tickRefresh >= refreshInterval) {
            draw();
            tickRefresh = 0;
        }

    }
    @EventHandler
    public void onMapInitialize(MapInitializeEvent e) {
        log("onMapInitialize");
    }
    //////////////////////////////////////////////////////////////////////
    //    このイベントは本人がマップを持った場合1tick
    //    他者がみる場合は1secの周期でよばれるため高速描写する必要がある
    //    実際の画像はbufferdImageに作成し、このイベントで転送する
    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        //  log("render");
        //     オフスクリーンバッファからコピー
        if (updateMapOnce) {
            canvas.drawImage(0, 0, bufferedImage);
            updateMapOnce  = false;
            if (debugMode) {
                //      描画回数を表示(debug)
                canvas.drawText( 4, 4, MinecraftFont.Font, key + "/map:" + mapId);
                canvas.drawText(4, 14, MinecraftFont.Font, "update:"+updateCount +"/"+this.refreshInterval+"tick");
                canvas.drawText( 4, 24, MinecraftFont.Font, "render:"+drawingTime+"ns");
            }
            updateCount++;
        }
        renderCount++;
    }



    public static boolean onPlayerInteractEntityEvent(PlayerInteractEntityEvent e) {

        Entity entity = e.getRightClicked();
        if (!(entity instanceof ItemFrame)) return false;

        //  クリックしたアイテムフレームのアイテムがマップでなければ抜け
        ItemFrame frame = (ItemFrame) entity;
        ItemStack item = frame.getItem();
        if (item.getType() != Material.FILLED_MAP) {
            return false;
        }

        int mapId = getMapId(item);
        String key = findKey(mapId);
        if (key == null) {
            return false;
        }
        Player player = e.getPlayer();

        //      たたいたブロック面
        BlockFace face = frame.getAttachedFace();

        //      叩いたブロック
        Block block = entity.getLocation().getBlock().getRelative(frame.getAttachedFace());
        //log(block.toString());
        World world = e.getPlayer().getWorld();

        //      叩いたブロックのBB
        BoundingBox bb = block.getBoundingBox();


        double rayDistance = 3;
        double rayAccuracy = 0.01;

        //     視線からのベクトルを得る
        RayTrace rayTrace = new RayTrace(player.getEyeLocation().toVector(), player.getEyeLocation().getDirection());
        /*
        ||     ベクトル表示
        || if (debugMode) {
        ||   rayTrace.highlight(player.getWorld(), rayDistance, rayAccuracy);
        || }
        */

        //      ディスプレイの　左上、右上をもとめる
        Vector topLeft = block.getLocation().toVector();
        Vector bottomRight = block.getLocation().toVector();
        topLeft.setY(topLeft.getY() + 1);
        if (face == BlockFace.WEST) {
            topLeft.setZ( topLeft.getZ() + 1);
            topLeft.setX( topLeft.getX() + 1);
            bottomRight.setX(bottomRight.getX() +1);
        }
        if (face == BlockFace.SOUTH) {
            topLeft.setX( topLeft.getX() + 1);
        }
        if (face == BlockFace.EAST) {
            bottomRight.setZ(bottomRight.getZ() +1);
        }
        if (face == BlockFace.NORTH) {
            bottomRight.setZ(bottomRight.getZ() +1);
            bottomRight.setX(bottomRight.getX() +1);
            topLeft.setZ( topLeft.getZ() + 1);
        }


        if (debugMode) {
            world.playEffect(topLeft.toLocation(world), Effect.SMOKE, 0);
            world.playEffect(bottomRight.toLocation(world), Effect.SMOKE, 0);
        }

        //      視線とブロックの交差点
        Vector hit = rayTrace.positionOfIntersection(bb, rayDistance, rayAccuracy);
        if (hit != null) {
            //      タッチした場所を光らす
            //  world.playEffect(hit.toLocation(world), Effect.COLOURED_DUST, 0);

            double aDis = hit.distance(topLeft);
            Vector left = topLeft.setY(hit.getY());
            double xDis = hit.distance(left);
            double y = sqrt(aDis*aDis - xDis*xDis);
            double dx = (double)128 * xDis;
            double dy = (double)128 * y;
            // dx -= 4;
            // dy -= 4;

            int px = (int)dx;
            int py = (int)dy;

            // player.sendMessage(px+","+py);

            //      タッチイベントを通知
            DisplayTouchFunction func = touchFunctions.get(key);
            if (func != null) {
                if (func.onDisplayTouch(key, mapId, player, px, py)) {
                    refresh(key);
                }
            }

        }
        //      回転イベントをキャンセル
        e.setCancelled(true);
        return true;
    }
    ///////////////////////////////////////
    //      ボタンイベントを検出する
    public static int onPlayerInteractEvent(PlayerInteractEvent e) {



        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) {
            return -1;
        }
        Location loc = clickedBlock.getLocation();

        /////////////////////////////////////////////////////
        //      プレートを踏んだ
        if (clickedBlock.getType() == Material.STONE_PRESSURE_PLATE) {

            // log("踏んだ ");

            //     クリックしたボタンの近くのエンティティを集める
            containsFrames(loc, 2, (mapId, key) -> {
                PlatePushFunction func = plateFunctions.get(key);
                if (func != null) {
                    log("プレートが踏まれた => map key = "+key);
                    if (func.onPlatePush(key, mapId, e.getPlayer())) {
                        refresh(key);
                    }
                }
            });
            return -1;
        }

        //      右ボタン以外は無視
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return  -1;
        }
        //
        if (e.getClickedBlock() == null) {
            return -1;
        }

        if (clickedBlock.getType() == Material.STONE_BUTTON) {
            //     クリックしたボタンの近くのエンティティを集める
            containsFrames(loc, 1, (mapId, key) -> {
                ButtonClickFunction func = buttonFunctions.get(key);
                if (func != null) {
                    log("ボタンが押された => map key = "+key);
                    if (func.onButtonClicked(key, mapId, e.getPlayer())) {
                        refresh(key);
                    }
                }
            });
        }
        return -1;
    }
    //          近くのアイテムフレームを集める
    public static void containsFrames(Location where, int range, BiConsumer<Integer, String> function) {
        for (Entity entity : where.getNearbyEntitiesByType(ItemFrame.class, range)) {
            //     アイテムフレームにあるのはマップか？
            ItemFrame frame = (ItemFrame) entity;
            ItemStack item = frame.getItem();
            if (item.getType() != Material.FILLED_MAP) {
                continue;
            }

            int mapId = getMapId(item);
            String key = findKey(mapId);
            if (key == null) {
                continue;
            }

            //      ボタン用メソッドをコール
            function.accept(mapId, key);
        }
    }


    static JavaPlugin plugin = null;
    /////////////////////////////////
    //          初期化
    /////////////////////////////////
    public static void setup(JavaPlugin plugin) {

        MappRenderer instance = MappRenderer.getInstance();
        getServer().getPluginManager().registerEvents(instance, plugin);


        loadPassMaps(plugin);
        setupMaps(plugin);
        MappRenderer.plugin = plugin;
    }



    //////////////////////////////////////////////////////////////////////
    ///    サーバーシャットダウンでレンダラはは初期化されてしまうので
    ///    再起動後にマップを作成する必要がある　
    ///    プラグインのonEnable()で　MappRenderer.setupMaps(this)
    //     で初期化して設定をロードすること
    static void setupMaps(JavaPlugin plugin) {

        Configuration config = plugin.getConfig();
        if (config.getStringList("Maps").size() == 0) {
            return;
        }
        List<String> mlist = config.getStringList("Maps");
        List<String> nmlist = new ArrayList<>();
        renderers.clear();


        log("setupMaps --------------------------");
        for (String ids : mlist) {

            //      mapId, keyのデータを取得
            String[] split = ids.split(",");
            int id = Integer.parseInt(split[0]);
            String  key = ids;
            if (split.length == 2) {
                key = split[1];
            }

            //     mapIDから新規にマップを作成する
            MapView map = Bukkit.getMap((short) id);
            if (map == null) {
                map = Bukkit.createMap(Bukkit.getWorlds().get(0));
            }
            for (MapRenderer mr : map.getRenderers()) {
                map.removeRenderer(mr);
            }

            MappRenderer renderer = new MappRenderer();

            renderer.updateMapOnce = true;
            renderer.refreshOnce = true;
            renderer.refreshInterval = drawRefreshTimeMap.getOrDefault(key, 0);
            renderer.key = key;
            renderer.mapId = id;
            renderer.initialize(map);

            //     レンダラを追加
            map.addRenderer(renderer);

            //     描画用に保存
            renderers.add(renderer);

            log("setupMap: key:"+key + "id:"+id);
            nmlist.add(ids);
        }

        //      マップを保存し直す
        config.set("Maps", nmlist);
        plugin.saveConfig();

        ////////////////////////////////
        //      タイマーを作成する
        Bukkit.getScheduler().runTaskTimer(plugin, MappRenderer::onTimerTick, 0, 1);

    }

    public static List<String> getAppList() {
        return new ArrayList<>(drawFunctions.keySet());
    }

    //////////////////////////////////////////
    /// 　   描画用マップを取得する
    ///     key : 描画を切り替えるためのキー
    public static ItemStack createMapItem(JavaPlugin plugin, String key) {


        if (drawFunctions.get(key) == null) {
            return null;
        }


        Configuration config = plugin.getConfig();

        List<String> mlist = config.getStringList("Maps");

        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
        int mapId = mapView.getId();

        setMapId(map, mapId);

        log("map" + mapId + " ");
        mlist.add(mapId + "," + key);

        //      設定データ保存
        config.set("Maps", mlist);
        plugin.saveConfig();

        /*
        || for (MapRenderer mr : mapView.getRenderers()) {
        ||     mapView.removeRenderer(mr);
        || }
        */
        mapView.getRenderers().clear();

        MappRenderer renderer = new MappRenderer();
        renderer.key = key;
        renderer.refreshOnce = true;
        renderer.updateMapOnce = true;
        renderer.mapId = mapId;
        mapView.addRenderer(renderer);

        //       識別用に保存
        renderers.add(renderer);
        //      初期化を呼ぶ　
        InitFunction func = initFunctions.get(key);
        if (func != null) {
            if (func.onInit(key, mapId)) {
                refresh(key);
            }
        }

        return map;
    }

    //      mapIdからキーを検索
    static String findKey(int mapId) {
        for(MappRenderer renderer : renderers) {
            if (renderer.mapId == mapId) {
                return renderer.key;
            }
        }
        return null;
    }


    //      描画する
    //      一致したキーの数を返す
    public static int refresh(String key) {

        if (key == null) {
            return 0;
        }
        int ret = 0;
        for(MappRenderer renderer : renderers) {
            if (renderer.key.equals(key)) {
                renderer.refreshOnce = true;
                ret++;
            }
        }

        return ret;
    }
    //      描画する
    //      一致したキーの数を返す
    public static int updateMap(String key) {

        if (key == null) {
            return 0;
        }
        int ret = 0;
        for(MappRenderer renderer : renderers) {
            if (renderer.key.equals(key)) {
                renderer.updateMapOnce = true;
                ret++;
            }
        }

        return ret;
    }

    public static void initAllMaps() {

        log("initAllMap");
        for(MappRenderer renderer : renderers) {
            InitFunction func = initFunctions.get(renderer.key);
            if (func == null) {
                continue;
            }
            //      初期化
            func.onInit(renderer.key, renderer.mapId);

        }

    }



    static HashMap<Player, Location> lastLocationMap = new HashMap<>();
    static HashMap<Player, Double> lastPitchMap = new HashMap<>();
    static HashMap<Player, Double> lastYawMap = new HashMap<>();

    static void onTimerTick() {
        ///////////////////////////////////////////////////
        //      向きの違いから検出しマウスのベロシティを求める
        for(Player p : Bukkit.getOnlinePlayers()) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType() != Material.FILLED_MAP) {
                continue;
            }
            int mapID = getMapId(item);
            String key = findKey(mapID);
            if (key == null) {
                continue;
            }

            Location lastLocation = lastLocationMap.get(p);
            
            Location location = p.getLocation();
            lastLocationMap.put(p, location);
            if (lastLocation == null) {
                continue;
            }
            
            double pitch1 = location.getPitch();
            double pitch2 = lastLocation.getPitch();
            double pitchVelocity = pitch1 - pitch2;

            Double lastPitch = lastPitchMap.getOrDefault(p, 0.0);
            if (pitchVelocity != lastPitch) {
                PlayerPitchFunction func = pitchFunctions.get(key);
                if (func != null) {
                    if (func.onPlayerPitchChanged(key, mapID, p, pitch1, pitchVelocity)) {
                        refresh(key);
                    }
                }

                lastPitchMap.put(p, pitchVelocity);
            }



            //      Ya左右の向き
            double yaw1 = location.getYaw();
            double yaw2 = lastLocation.getYaw();
            double yaw1Normalized = (yaw1 < 0) ? yaw1 + 360 : yaw1;
            double yaw2Normalized = (yaw2 < 0) ? yaw2 + 360 : yaw2;
            double velocity = yaw1Normalized - yaw2Normalized;
            if (velocity > 300) {
                velocity = yaw2Normalized + (360 - yaw1Normalized);
            } else if (velocity < -300) {
                velocity = yaw1Normalized - (360 - yaw2Normalized);
            }

            Double lastVelocity = lastYawMap.getOrDefault(p, 0.0);
            if (lastVelocity != velocity) {
                PlayerYawFunction func = yawFunctions.get(key);
                if (func != null) {
                    if (func.onPlayerYawChanged(key, mapID, p, yaw1Normalized, velocity)) {
                        refresh(key);
                    }
                }

                lastYawMap.put(p, velocity);
            }


            ////////////////////////////////////////
            //      マウスカーソル処理
            ////////////////////////////////////////
            Cursor cur = mouseCursor.get(mapID);
            if (cur != null) {
                if (cur.show) {
                    cur.x += velocity;
                    cur.y += pitchVelocity;

                    if (cur.x < 0) {
                        cur.x = 0;
                    }
                    if (cur.y < 0) {
                        cur.y = 0;
                    }
                    if (cur.x > 127) {
                        cur.x = 127;
                    }
                    if (cur.y > 127) {
                        cur.y = 127;
                    }
                }
            }
        }

        //      マップごとのTick処理
        for(MappRenderer renderer : renderers) {
            renderer.onTick();
        }



    }
    public static void updateAll() {

        log("UpdateAll");
        for(MappRenderer renderer : renderers) {
            renderer.refreshOnce = true;
            renderer.updateMapOnce = true;
        }
    }


    public static Graphics2D getGraphics(int mapId) {
        for(MappRenderer renderer : renderers) {
            if (renderer.mapId == mapId) {
                return renderer.bufferedImage.createGraphics();
            }
        }
        Bukkit.getLogger().warning("mapID" + mapId + "がみつからない！");
        return null;
    }

    //      イメージマップ　
    static HashMap<String, BufferedImage> imageMap = new HashMap<>();



    public static int listFolder(String directoryName, boolean subDir, ArrayList<File> files) {
        File directory = new File(directoryName);

        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) files.add(file);
            else if (file.isDirectory() && subDir) {
                listFolder(file.getAbsolutePath(), true, files);
            }
        }
        return files.size();
    }

    public static FileConfiguration getAppConfig(String appName) {
        String name = appName.endsWith(".yml") ? appName : appName + ".yml";
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }
    public static boolean saveAppConfig(String appName, FileConfiguration config) {
        String path = plugin.getDataFolder()+"/"+appName+".yml";
        try {
            config.save(path);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    ///////////////////////////////////////////////////
    //      プラグインフォルダの画像を読み込む
    static protected int loadPassMaps(JavaPlugin plugin) {

        imageMap.clear();
        int ret = 0;
        new File(plugin.getDataFolder(), File.separator + "PassMaps");

        ArrayList<File> files = new ArrayList<>();
        listFolder(plugin.getDataFolder() + "/PassMaps", true, files);

        for (File f : files) {
            if (f.isFile()) {
                String filename = f.getName();

                if (filename.substring(0, 1).toLowerCase(Locale.ROOT).equals(".")) {
                    continue;
                }

                String key = filename.substring(0, filename.lastIndexOf('.'));

                try {
                    BufferedImage image = ImageIO.read(new File(f.getAbsolutePath()));
                    imageMap.put(key, image);
                    log(key + " registered.");
                    ret++;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return ret;
    }



    /////////////////////////////////////
    //      キャッシュからイメージ取りだし
    static BufferedImage image(String key) {
        return imageMap.get(key);
    }

    /////////////////////////////////////////////
    public static void showCursor(int mapId) {

        Cursor cur = mouseCursor.get(mapId);
        if (cur == null) {
            cur = new Cursor();
            cur.x = 64;
            cur.y = 64;
        }
        cur.show = true;
        mouseCursor.put(mapId, cur);

    }
    public static void hideCursor(int mapId) {
        Cursor cur = mouseCursor.get(mapId);
        if (cur == null) {
            cur = new Cursor();
        }
        cur.show = false;
        mouseCursor.put(mapId, cur);


    }
}
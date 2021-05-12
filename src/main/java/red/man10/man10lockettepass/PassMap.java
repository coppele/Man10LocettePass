package red.man10.man10lockettepass;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PassMap {
    private final String key; // nameです。
    private final ItemFrame frame; // PassMapの置いてあるMapです。
    private final UUID owner; // オーナーです
    private final List<Block> doors; // 解錠された時に開くドアです。
    private int password = -1; // パスワードです。
    private int putPassword = -1; // 入力されたパスワードです。

    public PassMap(@NotNull String key, @NotNull ItemFrame frame, @NotNull Player owner) {
        this.key = key;
        this.frame = frame;
        this.owner = owner.getUniqueId();
        this.doors = new ArrayList<>();
    }

    ///////////////////////////////////////////////////////////////////////////
    //            SetterGetterです。

    public String getKey() {
        return key;
    }

    public ItemFrame getFrame() {
        return frame;
    }

    public UUID getUUID() {
        return frame.getUniqueId();
    }

    public UUID getOwner() {
        return owner;
    }

    public List<Block> getDoors() {
        return doors;
    }

    public int setPassword(int password) {
        return this.password = Math.abs(password);
    }
    public int getPassword() {
        return password;
    }

    public int getPutPassword() {
        return putPassword;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            一文字ずつタイピングします。
    public void typePassword(KeyBoard key) {
        if (putPassword == -1) putPassword = 0;
        if (key == KeyBoard.DELETE) putPassword /= 10;
        else if (key != KeyBoard.ENTER) putPassword = (putPassword * 10) + key.number();
    }
    ///////////////////////////////////////////////////////////////////////////
    //            入力したパスワードを消去します
    public void clearPutPassword() {
        putPassword = -1;
    }

    ///////////////////////////////////////////////////////////////////////////
    //            Stringにします。
    @Override
    public String toString() {
        Vector loc = frame.getLocation().clone().toBlockLocation().toVector();
        BlockFace face = frame.getFacing().getOppositeFace();
        return key + "§7 : §6" + password +
                " §7(" + frame.getWorld().getName() +
                "," + loc.toString() +
                "," + face.name().toLowerCase(Locale.ROOT) +
                ")";
    }

    public enum KeyBoard {
        SEVEN (-2,  3, 7  ), EIGHT(0,  3, 8), NINE (2,  3, 9  ),
        FOUR  (-2,  1, 4  ), FIVE (0,  1, 5), SIX  (2,  1, 6  ),
        ONE   (-2, -1, 1  ), TWO  (0, -1, 2), THREE(2, -1, 3  ),
        DELETE(-2, -3, 'D'), ZERO (0, -3, 0), ENTER(2, -3, 'E');
        private final Vector vector;
        KeyBoard(int x, int y, int number) {
            vector = new Vector(x, y, number);
        }
        public boolean matchVector(Vector v) {
            return vector.clone().setZ(0).equals(v);
        }
        public int number() {
            return (int) vector.getZ();
        }
        @Nullable
        public static KeyBoard getKey(Vector vector) {
            for (KeyBoard key : values()) {
                if (key.matchVector(vector)) {
                    return key;
                }
            }
            return null;
        }
    }
}

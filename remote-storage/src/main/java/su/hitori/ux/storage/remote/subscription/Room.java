package su.hitori.ux.storage.remote.subscription;

import su.hitori.ux.storage.remote.util.IDUtil;

public final class Room {

    private final String key;

    public Room(String key) {
        if (!IDUtil.validKey(key)) throw new IllegalArgumentException("key");

        this.key = key;
    }

}

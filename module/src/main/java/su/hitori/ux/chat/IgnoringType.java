package su.hitori.ux.chat;

import su.hitori.ux.storage.DataField;

import java.util.Set;
import java.util.UUID;

public enum IgnoringType {

    DIRECT_MESSAGES(Chat.DM_IGNORING_FIELD),
    CHAT(Chat.CHAT_IGNORING_FIELD);

    final DataField<Set<UUID>> field;

    IgnoringType(DataField<Set<UUID>> field) {
        this.field = field;
    }

}

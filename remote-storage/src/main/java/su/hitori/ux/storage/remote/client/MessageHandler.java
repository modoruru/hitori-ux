package su.hitori.ux.storage.remote.client;

import org.json.JSONObject;

public interface MessageHandler {

    void message(JSONObject messageBody);

}

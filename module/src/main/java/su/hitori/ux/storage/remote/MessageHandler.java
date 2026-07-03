package su.hitori.ux.storage.remote;

import org.json.JSONObject;

public interface MessageHandler {

    void message(JSONObject messageBody);

}

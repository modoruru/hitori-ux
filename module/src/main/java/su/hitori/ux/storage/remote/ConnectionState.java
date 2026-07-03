package su.hitori.ux.storage.remote;

enum ConnectionState {

    NEVER_OPENED,
    OPENED,
    AUTHORIZED,
    RECONNECTING,
    CLOSED

}

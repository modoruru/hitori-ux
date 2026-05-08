package su.hitori.ux.event;

import java.util.UUID;

public record Event(UUID uuid, String name, String description, long utcStartTime) {
}

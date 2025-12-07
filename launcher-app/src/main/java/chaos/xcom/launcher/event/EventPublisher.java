package chaos.xcom.launcher.event;

import chaos.xcom.launcher.event.dto.PropertyChangedEvent;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamSyncProgress;
import jakarta.enterprise.event.Event;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class EventPublisher {

    private final Event<Object> eventPublisher;

    public void publishAsync(PropertyChangedEvent propertyChangedEvent) {
        eventPublisher.fireAsync(propertyChangedEvent);
    }

    public void publishAsync(SteamMod steamMod) {
        eventPublisher.fireAsync(steamMod);
    }

    public void publishAsync(SteamSyncProgress steamSyncProgress) {
        eventPublisher.fireAsync(steamSyncProgress);
    }
}

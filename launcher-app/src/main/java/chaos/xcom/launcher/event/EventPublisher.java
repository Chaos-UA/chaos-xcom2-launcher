package chaos.xcom.launcher.event;

import chaos.xcom.launcher.event.dto.PropertyChangedEvent;
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
}

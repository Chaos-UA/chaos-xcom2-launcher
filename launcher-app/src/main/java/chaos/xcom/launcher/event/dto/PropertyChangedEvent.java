package chaos.xcom.launcher.event.dto;

import chaos.xcom.launcher.db.property.Property;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class PropertyChangedEvent {
    private final String key;
    private final Property oldProp;
    private final Property newProp;
}

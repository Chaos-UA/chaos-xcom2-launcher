package chaos.xcom.launcher.db.property;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
public class Property {
    private final String key;
    private final String value;
    private final String defaultValue;
    private final String description;
    private final Boolean isRequired;
    private final Instant createdAt;
    private final Instant updatedAt;
}

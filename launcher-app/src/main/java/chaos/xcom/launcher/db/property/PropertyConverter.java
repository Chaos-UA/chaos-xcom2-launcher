package chaos.xcom.launcher.db.property;

import chaos.db.gen.tables.records.PropertyRecord;
import jakarta.inject.Singleton;

@Singleton
public class PropertyConverter {

    public Property toProperty(PropertyRecord source) {
        return Property.builder()
                .key(source.getKey())
                .value(source.getValue())
                .description(source.getDescription())
                .isRequired(source.getIsRequired())
                .updatedAt(source.getUpdatedAt())
                .createdAt(source.getCreatedAt())
                .build();
    }

    public PropertyRecord toRecord(Property source) {
        PropertyRecord result = new PropertyRecord();
        result.setKey(source.getKey());
        result.setValue(source.getValue());
        result.setDescription(source.getDescription());
        result.setIsRequired(source.getIsRequired());
        result.setUpdatedAt(source.getUpdatedAt());
        result.setCreatedAt(source.getCreatedAt());
        return result;
    }
}

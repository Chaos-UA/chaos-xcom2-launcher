package chaos.xcom.launcher.db.property;

import chaos.db.gen.tables.records.PropertyRecord;
import chaos.xcom.launcher.db.TransactionSynchronizer;
import chaos.xcom.launcher.db.property.AbstractProperties.PropertyConfig;
import chaos.xcom.launcher.event.EventPublisher;
import chaos.xcom.launcher.event.dto.PropertyChangedEvent;
import chaos.xcom.launcher.exception.BadRequestException;
import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.exception.NotFoundException;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds cached in memory SCR properties.
 */
@Singleton
@Slf4j
@Startup
public class PropertyService {
    private final EventPublisher eventPublisher;
    private final TransactionSynchronizer txSynchronizer;
    private final HashMap<String, PropertyConfig<?>> properties;
    private final PropertyRepository propertyRepo;
    private final PropertyConverter propertyConverter;
    private final ConcurrentHashMap<String, Property> cache = new ConcurrentHashMap<>();

    public PropertyService(EventPublisher eventPublisher,
                           TransactionSynchronizer txSynchronizer,
                           PropertyRepository propertyRepo,
                           PropertyConverter propertyConverter,
                           Instance<AbstractProperties> propertiesConfigs) {
        this.eventPublisher = eventPublisher;
        this.txSynchronizer = txSynchronizer;
        this.propertyRepo = propertyRepo;
        this.propertyConverter = propertyConverter;
        this.properties = extractProperties(propertiesConfigs.stream().toList());
        initPropertiesAndBuildCache(this.properties);
    }

    public List<PropertyConfig<?>> getAllMissingRequiredProperties() {
        return properties.values().stream()
                .filter(v -> v.isRequiredProperty() && v.optional().isEmpty())
                .toList();
    }

    public List<Property> getAllProperties() {
        return properties.values().stream()
                .map(PropertyConfig::getProperty)
                .sorted((o1, o2) -> o1.getKey().compareToIgnoreCase(o2.getKey()))
                .collect(Collectors.toList());
    }

    protected Property getProperty(String key) {
        checkPropertyExist(key);
        if (!cache.containsKey(key)) {
            log.info("Loading '{}' property from DB into cache", key);
            PropertyRecord record = propertyRepo.find(key).get();
            Property property = propertyConverter.toProperty(record);
            cache.put(key, property);
            txSynchronizer.onRollback(() -> {
                cache.remove(key);
                log.info("Removed fetched property '{}' from cache after transaction failure", key);
            });
        }
        return cache.get(key);
    }

    public void setProperty(String key, String value) {
        Property property = getProperty(key);
        property = property.toBuilder() // copy
                .value(value)
                .build();
        save(property);
    }

    public void save(Property prop) {
        PropertyRecord property = propertyConverter.toRecord(prop);
        String key = property.getKey();
        String value = property.getValue();
        Instant now = Instant.now();
        if (property.getCreatedAt() == null) {
            property.setCreatedAt(now);
        }
        property.setUpdatedAt(now);
        PropertyConfig<?> propertyConfig = properties.get(key);
        if (propertyConfig == null) { // not mapped property, just save
            property.setIsRequired(false);
            log.warn("Ignoring not mapped and not used property {} with value {}", key, value);
            return;
        }

        if (value == null && propertyConfig.isRequiredProperty()) {
            throw new BadRequestException().message("Can't set null value to required '%s' property", key);
        }
        try {
            propertyConfig.stringToValue(value);
        } catch (Exception e) {
            throw new BadRequestException()
                    .message("Failed to parse property with key '%s'", key)
                    .cause(e);
        }
        Property oldProperty = propertyConfig.getProperty();
        String oldValue = oldProperty.getValue();
        property.setIsRequired(propertyConfig.isRequiredProperty());
        Property newProp = propertyConverter.toProperty(property);
        validateProperty(newProp);
        if (!isPropertyInCacheNotMatch(newProp)) {
            return; // no need to do request to DB to update
        }
        txSynchronizer.newTx(() -> propertyRepo.update(property));
        cache.put(key, newProp);
        boolean valueChanged = Objects.equals(value, propertyConfig.getValueAsString());
        log.info("Updated '{}' property to {} from {}, valueModified={}", key, value, oldValue, valueChanged);
        if (valueChanged) {
            publishAsync(new PropertyChangedEvent(propertyConfig.getKey(), oldProperty, newProp));
        }
    }

    private void publishAsync(PropertyChangedEvent propertyChangedEvent) {
        eventPublisher.publishAsync(propertyChangedEvent);
    }

    public int save(Collection<Property> properties) {
        for (Property property : properties) {
            save(property);
        }
        return properties.size();
    }

    private void validateProperty(Property property) {
        if (property.getIsRequired() && property.getValue() == null) {
            throw new InternalException().message("Missing value for required '%s' property", property.getKey());
        }
    }

    protected HashMap<String, PropertyConfig<?>> extractProperties(List<AbstractProperties> propertiesConfigs) {
        TreeSet<String> duplicateKeys = new TreeSet<>();
        HashMap<String, PropertyConfig<?>> result = new HashMap<>();
        for (AbstractProperties propertiesConfig : propertiesConfigs) {
            for (PropertyConfig<?> property : propertiesConfig.getProperties()) {
                String key = property.getKey();
                if (result.containsKey(key)) {
                    duplicateKeys.add(key);
                }
                result.put(property.getKey(), property);
            }
        }

        if (!duplicateKeys.isEmpty()) {
            throw new InternalException().message("Duplicate property keys '%s' detected", duplicateKeys);
        }
        return result;
    }

    protected ConcurrentHashMap<String, Property> initPropertiesAndBuildCache(
            Map<String, PropertyConfig<?>> properties) {

        Map<String, PropertyRecord> dbRecords = new CaseInsensitiveMap<>(propertyRepo.getAll().stream()
                .collect(Collectors.toMap(PropertyRecord::getKey, Function.identity())));

        properties = new CaseInsensitiveMap<>(properties);
        Map<String, PropertyRecord> propertiesToCreate = new CaseInsensitiveMap<>();
        Map<String, PropertyRecord> propertiesToUpdate = new CaseInsensitiveMap<>();
        ConcurrentHashMap<String, Property> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, PropertyConfig<?>> propEntry : properties.entrySet()) {
            String key = propEntry.getKey();
            PropertyConfig<?> property = propEntry.getValue();
            PropertyRecord dbRecord = dbRecords.get(key);
            if (dbRecord == null) {
                dbRecord = new PropertyRecord();
                maybeFillDbProperty(property, dbRecord);
                propertiesToCreate.put(key, dbRecord);
            } else if (maybeFillDbProperty(property, dbRecord)) {
                propertiesToUpdate.put(key, dbRecord);
            }
            result.put(key, propertyConverter.toProperty(dbRecord));
        }

        if (!propertiesToCreate.isEmpty() || !propertiesToUpdate.isEmpty()) {
            log.info("Properties to create: {}, update: {}",
                    propertiesToCreate.keySet(), propertiesToUpdate.keySet());
            propertyRepo.insert(propertiesToCreate.values());
            propertyRepo.update(propertiesToUpdate.values());
        }
        cache.putAll(result);
        return result;
    }

    protected boolean maybeFillDbProperty(PropertyConfig<?> property, PropertyRecord dbRecord) {
        Instant now = Instant.now();
        boolean newRecord = dbRecord.getKey() == null;
        if (!Objects.equals(property.getKey(), dbRecord.getKey())
                || !Objects.equals(property.getDefaultValue(), dbRecord.getDefaultValue())
                || !Objects.equals(property.isRequiredProperty(), dbRecord.getIsRequired())
                || !Objects.equals(property.getDescription(), dbRecord.getDescription())) {

            dbRecord.setKey(property.getKey());
            dbRecord.setDescription(property.getDescription());
            dbRecord.setIsRequired(property.isRequiredProperty());

            if (newRecord || dbRecord.getDefaultValue() == null && property.isRequiredProperty()) {
                dbRecord.setDefaultValue(property.getDefaultValueAsString());
            }

            if (newRecord || dbRecord.getValue() == null && property.isRequiredProperty()) {
                dbRecord.setValue(property.getDefaultValueAsString());
            }

            if (newRecord) {
                dbRecord.setCreatedAt(now);
            }
            dbRecord.setUpdatedAt(now);
            return true;
        }
        return false;
    }

    private void checkPropertyExist(String key) {
        if (!properties.containsKey(key)) {
            throw new NotFoundException().message("Property '%s' doesn't exist", key);
        }
    }

    private boolean isPropertyInCacheNotMatch(Property newProp) {
        Property property = cache.get(newProp.getKey());
        if (property == null) {
            return true;
        }
        boolean same = Objects.equals(property.getValue(), newProp.getValue())
                && Objects.equals(property.getDefaultValue(), newProp.getDefaultValue())
                && Objects.equals(property.getIsRequired(), newProp.getIsRequired()
                && Objects.equals(property.getDescription(), newProp.getDescription()));
        return !same;
    }

}

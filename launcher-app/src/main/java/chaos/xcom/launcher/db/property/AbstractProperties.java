package chaos.xcom.launcher.db.property;

import chaos.xcom.launcher.common.JsonConverter;
import chaos.xcom.launcher.exception.InternalException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@RequiredArgsConstructor
public abstract class AbstractProperties {
    protected final Instance<PropertyService> propertyService;
    private final List<PropertyConfig<?>> properties = new ArrayList<>();

    protected List<PropertyConfig<?>> getProperties() {
        return properties;
    }

    protected <T extends PropertyConfig<?>> T prop(T propertyConfig) {
        properties.add(propertyConfig);
        return propertyConfig;
    }

    protected <T> ListProperty<T> listProp(String key, TypeReference<List<T>> valueType, List<T> defaultValue, String description) {
        ListProperty<T> result = new ListProperty<>(key, valueType, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected <T> JsonProperty<T> jsonProp(String key, TypeReference<T> valueType, T defaultValue, String description) {
        JsonProperty<T> result = new JsonProperty<>(key, valueType, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected InstantProperty requiredInstantProp(String key, Instant defaultValue, String description) {
        InstantProperty result = new InstantProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected StringProperty requiredStringProp(String key) {
        StringProperty result = new StringProperty(key, null, null, propertyService);
        properties.add(result);
        return result;
    }

    protected StringProperty requiredStringProp(String key, String defaultValue, String description) {
        StringProperty result = new StringProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected OptionalStringProperty optionalStringProp(String key) {
        return optionalStringProp(key, null);
    }

    protected OptionalStringProperty optionalStringProp(String key, String description) {
        return optionalStringProp(key, null, description);
    }

    protected OptionalStringProperty optionalStringProp(String key, String defaultValue, String description) {
        var result = new OptionalStringProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected IntProperty requiredIntProp(String key) {
        return requiredIntProp(key, null, null);
    }

    protected IntProperty requiredIntProp(String key, Integer defaultValue, String description) {
        IntProperty result = new IntProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected LongProperty requiredLongProp(String key, long defaultValue, String description) {
        LongProperty result = new LongProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected DoubleProperty requiredDoubleProp(String key) {
        return requiredDoubleProp(key, null, null);
    }

    protected DoubleProperty requiredDoubleProp(String key, Double defaultValue, String description) {
        DoubleProperty result = new DoubleProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected OptionalLongProperty optionalLongProp(String key, Long defaultValue, String description) {
        OptionalLongProperty result = new OptionalLongProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected BooleanProperty booleanProp(String key) {
        return booleanProp(key, null, null);
    }

    protected BooleanProperty booleanProp(String key, Boolean defaultValue, String description) {
        BooleanProperty result = new BooleanProperty(key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    protected <T extends Enum<T>> EnumProperty<T> requiredEnumProp(Class<T> enumCls, String key) {
        return requiredEnumProp(enumCls, key, null, null);
    }

    protected <T extends Enum<T>> EnumProperty<T> requiredEnumProp(Class<T> enumCls, String key,
                                                                   T defaultValue, String description) {
        EnumProperty<T> result = new EnumProperty<>(enumCls, key, defaultValue, description, propertyService);
        properties.add(result);
        return result;
    }

    public abstract static class PropertyConfig<T> {
        protected final String key;
        protected final T defaultValue;
        protected final boolean requiredProperty;
        protected final String description;
        protected final Instance<PropertyService> propertyService;

        public PropertyConfig(String key,
                              T defaultValue,
                              boolean requiredProperty,
                              String description,
                              Instance<PropertyService> propertyService) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.requiredProperty = requiredProperty;
            this.description = description;
            this.propertyService = propertyService;
        }

        public String getKey() {
            return key;
        }

        public Optional<T> optional() {
            String value = getPropertyService().getProperty(key).getValue();
            if (value == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(stringToValue(value));
        }

        /**
         * Return property or throw exception if not available.
         */
        public T get() {
            String value = getPropertyService().getProperty(key).getValue();
            if (value == null) {
                throw new InternalException().message("Property '%s' missing value", key);
            }
            return stringToValue(value);
        }

        public void set(T value) {
            getPropertyService().setProperty(key, valueToString(value));
        }

        public boolean isPresent() {
            return optional().isPresent();
        }

        protected T getDefaultValue() {
            return defaultValue;
        }

        protected String getDefaultValueAsString() {
            return valueToString(defaultValue);
        }

        protected boolean isRequiredProperty() {
            return requiredProperty;
        }

        protected boolean isOptionalProperty() {
            return !requiredProperty;
        }

        protected String valueToString(T value) {
            return value == null ? null : value.toString();
        }

        protected abstract T stringToValue(String value);

        protected String getDescription() {
            return description;
        }

        protected String getValueAsString() {
            return getPropertyService().getProperty(key).getValue();
        }

        protected PropertyService getPropertyService() {
            return propertyService.get();
        }

        protected Property getProperty() {
            return getPropertyService().getProperty(key);
        }

        @Override
        public String toString() {
            return "Property{"
                    + "key='" + key + '\''
                    + ", defaultValue=" + defaultValue
                    + ", requiredProperty=" + requiredProperty
                    + ", description='" + description + '\''
                    + '}';
        }
    }

    public static class ListProperty<T> extends JsonProperty<List<T>> {

        protected ListProperty(String key,
                               TypeReference<List<T>> valueType,
                               List<T> defaultValue,
                               String description,
                               Instance<PropertyService> propertyService) {
            super(key, valueType, defaultValue, description, propertyService);
        }

        /**
         * @return true if added
         */
        public boolean addUnique(T value) {
            if (get().contains(value)) {
                return false;
            }
            TreeSet<T> treeSet = new TreeSet<>(get());
            treeSet.add(value);
            set(new ArrayList<>(treeSet));
            return true;
        }

        /**
         * @return true if removed
         */
        public boolean remove(T value) {
            ArrayList<T> values = new ArrayList<>(get());
            if (!values.remove(value)) {
                return false;
            }
            set(values);
            return true;
        }

        @Override
        protected List<T> stringToValue(String value) {
            if (value == null) {
                return List.of();
            }
            return super.stringToValue(value);
        }

        @Override
        protected String valueToString(List<T> value) {
            if (value == null) {
                value = List.of();
            }
            return super.valueToString(value);
        }
    }

    public static class JsonProperty<T> extends PropertyConfig<T> {

        protected final TypeReference<T> valueType;

        protected JsonProperty(String key,
                               TypeReference<T> valueType,
                               T defaultValue,
                               String description,
                               Instance<PropertyService> propertyService) {
            super(key, defaultValue, false, description, propertyService);
            this.valueType = valueType;
        }

        @Override
        protected T stringToValue(String value) {
            return JsonConverter.getInstance().parse(value, valueType);
        }

        @Override
        protected String valueToString(T value) {
            return JsonConverter.getInstance().toJson(value);
        }
    }

    public static class InstantProperty extends PropertyConfig<Instant> {

        public InstantProperty(String key, Instant defaultValue, String description,
                               Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        @Override
        protected Instant stringToValue(String value) {
            return Instant.parse(value);
        }
    }

    public static class StringProperty extends PropertyConfig<String> {

        protected StringProperty(String key,
                                 String defaultValue,
                                 String description,
                                 Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        @Override
        protected String stringToValue(String value) {
            return value;
        }
    }

    public static class IntProperty extends PropertyConfig<Integer> {

        protected IntProperty(String key, Integer defaultValue, String description,
                              Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        @Override
        protected Integer stringToValue(String value) {
            return value == null ? null : Integer.parseInt(value);
        }
    }

    public static class LongProperty extends PropertyConfig<Long> {

        protected LongProperty(String key, long defaultValue, String description,
                               Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        @Override
        protected Long stringToValue(String value) {
            return value == null ? null : Long.parseLong(value);
        }
    }

    public static class DoubleProperty extends PropertyConfig<Double> {

        protected DoubleProperty(String key,
                                 Double defaultValue,
                                 String description,
                                 Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        @Override
        protected Double stringToValue(String value) {
            return Double.parseDouble(value);
        }
    }

    public static class BooleanProperty extends PropertyConfig<Boolean> {

        protected BooleanProperty(String key,
                                  Boolean defaultValue,
                                  String description,
                                  Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
        }

        public boolean isTrue() {
            return get();
        }

        public boolean isFalse() {
            return !get();
        }

        @Override
        protected Boolean stringToValue(String value) {
            if ("true".equals(value)) {
                return true;
            } else if ("false".equals(value)) {
                return false;
            } else {
                throw new InternalException().message("Property '%s' has incorrect boolean value: %s", key, value);
            }
        }
    }

    public static class EnumProperty<T extends Enum<T>> extends PropertyConfig<T> {

        private final Class<T> enumClass;

        protected EnumProperty(Class<T> enumCls,
                               String key,
                               T defaultValue,
                               String description,
                               Instance<PropertyService> propertyService) {
            super(key, defaultValue, true, description, propertyService);
            this.enumClass = enumCls;
        }

        @Override
        protected T stringToValue(String value) {
            if (value == null) {
                return null;
            }
            return Enum.valueOf(enumClass, value);
        }
    }

    public abstract static class OptionalProperty<T> extends PropertyConfig<T> {

        protected OptionalProperty(String key, T defaultValue, String description,
                                   Instance<PropertyService> propertyService) {
            super(key, defaultValue, false, description, propertyService);
        }

        public abstract T stringToValue(String value);
    }

    public static class OptionalStringProperty extends OptionalProperty<String> {

        protected OptionalStringProperty(String key,
                                         String defaultValue,
                                         String description,
                                         Instance<PropertyService> propertyService) {
            super(key, defaultValue, description, propertyService);
        }

        @Override
        public String stringToValue(String value) {
            return value;
        }

    }

    public static class OptionalLongProperty extends OptionalProperty<Long> {

        protected OptionalLongProperty(String key,
                                       Long defaultValue,
                                       String description,
                                       Instance<PropertyService> propertyService) {
            super(key, defaultValue, description, propertyService);
        }

        @Override
        public Long stringToValue(String value) {
            return value == null ? null : Long.parseLong(value);
        }
    }

}

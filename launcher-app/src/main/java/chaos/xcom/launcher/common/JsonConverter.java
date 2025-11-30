package chaos.xcom.launcher.common;

import chaos.xcom.launcher.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@Startup
@RequiredArgsConstructor
public class JsonConverter {

    private final ObjectMapper mapper;

    public static JsonConverter getInstance() {
        return CDI.current().select(JsonConverter.class).get();
    }

    public String toJson(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new AppException().message("Failed to convert to JSON").cause(e);
        }
    }

    public <T> T parse(String value, TypeReference<T> valueType) {
        try {
            if (value == null) {
                return null;
            }
            return mapper.readValue(value, valueType);
        } catch (Exception e) {
            throw new AppException().message("Failed to convert to JSON").cause(e);
        }
    }
}

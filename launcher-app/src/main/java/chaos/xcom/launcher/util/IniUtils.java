package chaos.xcom.launcher.util;

import chaos.xcom.launcher.exception.AppException;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.io.FileHandler;

import java.io.File;
import java.util.HashMap;

public class IniUtils {

    public static INIConfiguration loadProperties(File file) {
        try {
            INIConfiguration config = new INIConfiguration();
            FileHandler handler = new FileHandler(config);
            handler.load(file);

            HashMap<String, String> result = new HashMap<>(config.size());

            return config;
        } catch (Exception e) {
            throw new AppException("Failed to load properties from: " + file.getAbsolutePath()).cause(e);
        }
    }

}

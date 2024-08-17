package org.carecode.mw.lims.mw.indiko;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.MiddlewareSettings;

public class SettingsLoader {

    private static final Logger logger = LogManager.getLogger(SettingsLoader.class);
    private static MiddlewareSettings middlewareSettings;

    public static void loadSettings() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("config.json")) {
            middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
            logger.info("Settings loaded from config.json");
        } catch (IOException e) {
            logger.error("Failed to load settings from config.json", e);
        }
    }

    public static MiddlewareSettings getSettings() {
        if (middlewareSettings == null) {
            loadSettings();
        }
        return middlewareSettings;
    }
}

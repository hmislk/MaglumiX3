package org.carecode.mw.lims.mw.indiko;

import java.io.FileReader;
import java.io.IOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SettingsLoader {

    private static final Logger logger = LogManager.getLogger(SettingsLoader.class);
    private static JsonObject settings;

    public static void loadSettings() {
        try (FileReader reader = new FileReader("config.json")) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            settings = jsonElement.getAsJsonObject();
            logger.info("Settings loaded from config.json");
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load settings from config.json", e);
        }
    }

    public static JsonObject getSettings() {
        if (settings == null) {
            loadSettings();
        }
        return settings;
    }
}

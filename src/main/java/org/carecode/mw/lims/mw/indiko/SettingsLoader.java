package org.carecode.mw.lims.mw.indiko;

import java.io.FileReader;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class SettingsLoader {
    public static JsonObject settings;

    public static void loadSettings() {
        try (FileReader reader = new FileReader("config.json")) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(reader);
            settings = jsonElement.getAsJsonObject();
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
    }
}

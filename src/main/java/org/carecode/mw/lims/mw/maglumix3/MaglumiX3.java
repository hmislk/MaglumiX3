package org.carecode.mw.lims.mw.maglumix3;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 *
 * @author Buddhika
 */
public class MaglumiX3 {

    private static JsonObject settings;

    public static void main(String[] args) {
        System.out.println("MDGPHM");
        SettingsLoader.loadSettings();

        // Start thread to listen for analyzer messages
        Thread analyzerListenerThread = new Thread(new AnalyzerListener());
        analyzerListenerThread.start();

        // Start thread to periodically send requests to LIS
        Thread lisCommunicatorThread = new Thread(new LisCommunicator());
        lisCommunicatorThread.start();
    }

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

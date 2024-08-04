package org.carecode.mw.lims.mw.indiko;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonObject;

public class Indiko {

    private static final Logger logger = LogManager.getLogger(Indiko.class);

    public static void main(String[] args) {
        logger.info("Starting Indiko middleware...");

        try {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load settings.", e);
            return;
        }

        JsonObject middlewareSettings = SettingsLoader.getSettings().getAsJsonObject("middlewareSettings");
        int port = middlewareSettings.get("middlewarePort").getAsInt();


        IndikoServer server = new IndikoServer();
        server.start(port);
    }
}

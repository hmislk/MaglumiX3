package org.carecode.mw.lims.mw.indiko;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.google.gson.JsonObject;

public class ResultProcessor {

    public static void pushResults() {
        try {
            JsonObject results = AnalyzerCommunicator.getResultsFromAnalyzer();
            JsonObject limsSettings = SettingsLoader.settings.getAsJsonObject("middlewareSettings").getAsJsonObject("limsSettings");
            String pushResultsEndpoint = limsSettings.get("pushResultsEndpoint").getAsString();

            URL url = new URL(pushResultsEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(results.toString().getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Results sent successfully");
            } else {
                System.out.println("POST request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

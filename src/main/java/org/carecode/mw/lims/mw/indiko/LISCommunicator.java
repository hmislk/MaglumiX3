package org.carecode.mw.lims.mw.indiko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LISCommunicator {

    public static void pullSampleData() {
        try {
            JsonObject limsSettings = SettingsLoader.getSettings().getAsJsonObject("middlewareSettings").getAsJsonObject("limsSettings");
            String pullSampleDataEndpoint = limsSettings.get("pullSampleDataEndpoint").getAsString();

            URL url = new URL(pullSampleDataEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Process the response
                JsonObject responseObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                handleSampleData("");
            } else {
                System.out.println("GET request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String  handleSampleData(String data) {
        return "";
    }
}

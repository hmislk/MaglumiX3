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
            JsonObject limsSettings = SettingsLoader.settings.getAsJsonObject("middlewareSettings").getAsJsonObject("limsSettings");
            String pullSampleDataEndpoint = limsSettings.get("pullSampleDataEndpoint").getAsString();

            System.out.println("Making GET request to: " + pullSampleDataEndpoint);

            URL url = new URL(pullSampleDataEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

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
                System.out.println("Response: " + responseObject.toString());

                if (false) {
                    handleSampleData(responseObject);
                }
            } else {
                System.out.println("GET request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleSampleData(JsonObject data) {
        // Process the sample data and forward requests to the analyzer
        for (JsonElement sampleElement : data.getAsJsonArray("samples")) {
            JsonObject sample = sampleElement.getAsJsonObject();
            String sampleId = sample.get("id").getAsString();
            JsonArray tests = sample.getAsJsonArray("tests");
            AnalyzerCommunicator.sendRequestToAnalyzer(sampleId, tests);
        }
    }
}

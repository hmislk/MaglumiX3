package org.carecode.mw.lims.mw.maglumix3;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

public class LisCommunicator implements Runnable {

    @Override
    public void run() {
        Timer timer = new Timer();
        timer.schedule(new RequestLISDataTask(), 0, 60000); // Schedule every 60 seconds
    }

    static class RequestLISDataTask extends TimerTask {
        public void run() {
            requestTestRequestsFromLIS();
        }
    }

    public static void requestTestRequestsFromLIS() {
        try {
            String urlString = SettingsLoader.settings.getAsJsonObject("middlewareSettings")
                    .getAsJsonObject("limsSettings")
                    .get("pullSampleDataEndpoint").getAsString();
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String jsonResponse = response.toString();
                processLISResponse(jsonResponse);

                System.out.println("LIS data request successful.");
            } else {
                System.out.println("LIS data request failed.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processLISResponse(String jsonResponse) {
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonResponse);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonArray samplesArray = jsonObject.getAsJsonArray("samples");

            if (samplesArray.size() == 0) {
                System.out.println("No samples found in the response.");
                // Handle empty response
                handleEmptyResponse();
            } else {
                System.out.println("Processing samples from the response.");
                // Handle non-empty response
                handleNonEmptyResponse(samplesArray);
            }

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    private static void handleEmptyResponse() {
        // Implement logic to handle an empty response
        System.out.println("Handling empty response from LIS.");
    }

    private static void handleNonEmptyResponse(JsonArray samplesArray) {
        // Implement logic to handle non-empty response
        System.out.println("Handling non-empty response from LIS.");
        for (JsonElement sampleElement : samplesArray) {
            JsonObject sample = sampleElement.getAsJsonObject();
            String sampleId = sample.get("sampleId").getAsString();
            JsonArray testCodes = sample.getAsJsonArray("testCodes");

            System.out.println("Sample ID: " + sampleId);
            System.out.println("Test Codes: " + testCodes);
            
            // Prepare the sample JSON to send to the analyzer
            JsonObject sampleRequest = new JsonObject();
            sampleRequest.addProperty("sampleId", sampleId);
            sampleRequest.add("testCodes", testCodes);

            // Send the request to the analyzer
            AnalyzerCommunicator.sendTestRequestsToAnalyzer(sampleRequest);

            // Further processing of the sample and test codes
        }
    }

    public static void sendTestResultsToLIS(JsonObject testResults) {
        try {
            String urlString = SettingsLoader.settings.getAsJsonObject("middlewareSettings")
                    .getAsJsonObject("limsSettings")
                    .get("pushResultsEndpoint").getAsString();
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = testResults.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Test results sent successfully.");
            } else {
                System.out.println("Failed to send test results.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package org.carecode.mw.lims.mw.indiko;

import com.google.gson.Gson;
import org.json.JSONObject;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.PatientDataBundle;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class LISCommunicator {

//    static boolean testing = true;
    private static final Gson gson = new Gson();

    public static PatientDataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
//        if (testing) {
//            PatientDataBundle pdb = new PatientDataBundle();
//            List<String> testNames = Arrays.asList("HDL", "RF2");
//            OrderRecord or = new OrderRecord(0, queryRecord.getSampleId(), testNames, "S", new Date(), "testInformation");
//            pdb.getOrderRecords().add(or);
//            PatientRecord pr = new PatientRecord(0, "1010101", "111111", "Buddhika Ariyaratne", "M H B", "Male", "Sinhalese", null, "Galle", "0715812399", "Dr Niluka");
//            pdb.setPatientRecord(pr);
//            return pdb;
//        }

        try {
            String postSampleDataEndpoint = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(postSampleDataEndpoint + "/test_orders_for_sample_requests");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // Convert QueryRecord to JSON
            String jsonInputString = gson.toJson(queryRecord);

            // Send the request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Process response
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Convert the response to a PatientDataBundle object
                PatientDataBundle patientDataBundle = gson.fromJson(response.toString(), PatientDataBundle.class);

                return patientDataBundle;
            } else {
                System.out.println("POST request failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

   
    public static void pushResults(PatientDataBundle patientDataBundle) {
        try {
            System.out.println("SettingsLoader.getSettings() = " + SettingsLoader.getSettings());
            System.out.println("SettingsLoader.getSettings().getLimsSettings() = " + SettingsLoader.getSettings().getLimsSettings());
            System.out.println("SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() = " + SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl());
            String pushResultsEndpoint = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() + "/test_results";
            URL url = new URL(pushResultsEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // Serialize PatientDataBundle to JSON
            String jsonInputString = gson.toJson(patientDataBundle);

            // Send the JSON in the request body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Optionally process the server response (if needed)
                JsonObject responseObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                System.out.println("Response from server: " + responseObject.toString());
            } else {
                System.out.println("POST request failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

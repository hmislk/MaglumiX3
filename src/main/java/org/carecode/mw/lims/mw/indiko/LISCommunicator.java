package org.carecode.mw.lims.mw.indiko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.xml.crypto.Data;

public class LISCommunicator {

    static boolean testing = true;

    public static PatientDataBundle pullOrders(QueryRecord queryRecord) {
        if (testing) {
            PatientDataBundle pdb = new PatientDataBundle();
            List<String> testNames = Arrays.asList("GLU", "ALB");
            OrderRecord or = new OrderRecord(0, queryRecord.getSampleId(), testNames, "S", new Date(), "testInformation");
            pdb.getOrderRecords().add(or);
            PatientRecord pr = new PatientRecord(0, "1010101", "111111", "Buddhika Ariyaratne", "M H B", "Male", "Sinhalese", null, "Galle", "0715812399", "Dr Niluka");
            pdb.setPatientRecord(pr);
            return pdb;
        }
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
            } else {
                System.out.println("GET request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void pushResults(PatientDataBundle patientDataBundle) {

        if (testing) {
            // Output all records for testing purposes
            System.out.println("Testing mode: Outputting patient data bundle details.");

            // Output patient record
            PatientRecord patientRecord = patientDataBundle.getPatientRecord();
            if (patientRecord != null) {
                System.out.println("Patient ID: " + patientRecord.getPatientId());
                System.out.println("Patient Name: " + patientRecord.getPatientName());
                // Additional patient details can be printed here
            }

            // Output results records
            List<ResultsRecord> resultsRecords = patientDataBundle.getResultsRecords();
            if (resultsRecords != null && !resultsRecords.isEmpty()) {
                System.out.println("Results Records:");
                for (ResultsRecord record : resultsRecords) {
                    System.out.println("Test Code: " + record.getTestCode() + ", Result: " + record.getResultValue());
                }
            }

            // Output order records
            List<OrderRecord> orderRecords = patientDataBundle.getOrderRecords();
            if (orderRecords != null && !orderRecords.isEmpty()) {
                System.out.println("Order Records:");
                for (OrderRecord record : orderRecords) {
                    System.out.println("Sample ID: " + record.getSampleId() + ", Test Names: " + String.join(", ", record.getTestNames()));
                }
            }

            // Output query records
            List<QueryRecord> queryRecords = patientDataBundle.getQueryRecords();
            if (queryRecords != null && !queryRecords.isEmpty()) {
                System.out.println("Query Records:");
                for (QueryRecord record : queryRecords) {
                    System.out.println("Sample ID: " + record.getSampleId() + ", Query Type: " + record.getQueryType());
                }
            }
        }
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

            } else {
                System.out.println("GET request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

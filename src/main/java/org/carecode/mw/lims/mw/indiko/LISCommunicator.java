package org.carecode.mw.lims.mw.indiko;

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

    static boolean testing = true;

    public static PatientDataBundle pullOrders(QueryRecord queryRecord) {
        if (testing) {
            PatientDataBundle pdb = new PatientDataBundle();
            List<String> testNames = Arrays.asList("HDL, RF2");
            OrderRecord or = new OrderRecord(0, queryRecord.getSampleId(), testNames, "S", new Date(), "testInformation");
            pdb.getOrderRecords().add(or);
            PatientRecord pr = new PatientRecord(0, "1010101", "111111", "Buddhika Ariyaratne", "M H B", "Male", "Sinhalese", null, "Galle", "0715812399", "Dr Niluka");
            pdb.setPatientRecord(pr);
            return pdb;
        }
        try {
            String pullSampleDataEndpoint = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(pullSampleDataEndpoint + "/pull_requests");
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

    public static void sendObservationsToLims(List<Map.Entry<String, String>> observations, Date date) {
        Indiko.logger.info("Sending observations to LIMS");

        // Determine the file name for the day's sample IDs
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String fileName = "processed_samples_" + dateFormat.format(date) + ".txt";
        Set<String> processedSamples = new HashSet<>();

        // Load the processed samples from the file if it exists
        try {
            Path path = Paths.get(fileName);
            if (Files.exists(path)) {
                System.out.println("Loading processed samples from file: " + fileName);
                processedSamples.addAll(Files.readAllLines(path));
            }
        } catch (IOException e) {
            Indiko.logger.error("Error reading processed samples file: " + fileName, e);
        }

        // Prepare to write to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            for (Map.Entry<String, String> entry : observations) {
                String sampleId = entry.getKey();
                String observationValue = entry.getValue();

                // Check if the sample ID has already been processed
                if (!processedSamples.contains(sampleId)) {
                    System.out.println("Processing sample ID: " + sampleId + " with HbA1c value: " + observationValue);

                    // Create a custom JSON object to represent the observation
                    JSONObject observationJson = new JSONObject();
                    observationJson.put("sampleId", sampleId);
                    observationJson.put("observationValue", observationValue);
                    observationJson.put("analyzerId", SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerId());
                    observationJson.put("departmentAnalyzerId", SettingsLoader.getSettings().getAnalyzerDetails().getDepartmentAnalyzerId());
                    observationJson.put("analyzerName", SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerName());
                    observationJson.put("departmentId", SettingsLoader.getSettings().getAnalyzerDetails().getDepartmentId());
                    observationJson.put("username", SettingsLoader.getSettings().getLimsSettings().getUsername());
                    observationJson.put("password", SettingsLoader.getSettings().getLimsSettings().getPassword());

                    // Additional attributes for HbA1c percentage
                    observationJson.put("observationValueCodingSystem", "http://loinc.org");
                    observationJson.put("observationValueCode", "4548-4"); // Code for HbA1c as a percentage
                    observationJson.put("observationUnitCodingSystem", "http://unitsofmeasure.org");
                    observationJson.put("observationUnitCode", "%"); // Unit code for percentage

                    // Log the JSON object
                    System.out.println("Prepared Observation JSON: " + observationJson.toString(4));

                    // Send the JSON object to the LIMS server
                    System.out.println("Sending observation to LIMS server...");
                    sendJsonToLimsServer(observationJson);

                    // Write the sample ID to the file
                    writer.write(sampleId);
                    writer.newLine();

                    // Add the sample ID to the processed set
                    processedSamples.add(sampleId);
                } else {
                    Indiko.logger.info("Sample ID " + sampleId + " has already been processed for today.");
                }
            }
        } catch (IOException e) {
            Indiko.logger.error("Error writing to processed samples file: " + fileName, e);
        }
    }

    public static void sendJsonToLimsServer(JSONObject observationJson) {
        Indiko.logger.info("Preparing to send JSON to LIMS server");

        try {
            // Log the JSON being sent
            Indiko.logger.info("Observation JSON: " + observationJson.toString(4));

            // Create the URL and open the connection
            URL url = new URL(SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() + "/observation");
            Indiko.logger.info("LIMS Server URL: " + url.toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set connection properties
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");

            // Add Basic Authentication header
            String auth = SettingsLoader.getSettings().getLimsSettings().getUsername() + ":" + SettingsLoader.getSettings().getLimsSettings().getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            Indiko.logger.info("Authorization Header: Basic " + encodedAuth);

            // Send JSON data
            Indiko.logger.info("Sending data...");
            OutputStream os = connection.getOutputStream();
            os.write(observationJson.toString().getBytes());
            os.flush();
            os.close();

            // Get response code
            int responseCode = connection.getResponseCode();
            Indiko.logger.info("Response Code: " + responseCode);

            // Handle server response
            if (responseCode != HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader((connection.getErrorStream())));
                String output;
                Indiko.logger.error("Error from Server:");
                while ((output = br.readLine()) != null) {
                    Indiko.logger.error(output);
                }
                br.close();
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
                String output;
                Indiko.logger.info("Response from Server:");
                while ((output = br.readLine()) != null) {
                    Indiko.logger.info(output);
                }
                br.close();
            }

            connection.disconnect();

        } catch (Exception e) {
            Indiko.logger.error("Exception occurred while sending JSON to LIMS server", e);
        }
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
            
            String pullSampleDataEndpoint = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();

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

package org.carecode.mw.lims.mw.maglumix3;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AnalyzerCommunicator {

    private static Socket socket;
    private static OutputStream os;
    private static InputStream is;

    public static void startAnalyzerCommunication() {
        try {
            String analyzerIP = SettingsLoader.settings.getAsJsonObject("middlewareSettings")
                    .getAsJsonObject("analyzerDetails")
                    .get("analyzerIP").getAsString();
            int analyzerPort = SettingsLoader.settings.getAsJsonObject("middlewareSettings")
                    .getAsJsonObject("analyzerDetails")
                    .get("analyzerPort").getAsInt();

            // Open the socket once
            socket = new Socket(analyzerIP, analyzerPort);
            os = socket.getOutputStream();
            is = socket.getInputStream();

            // Start listening for messages in a separate thread
            Thread listenerThread = new Thread(() -> listenForAnalyzerMessages());
            listenerThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void listenForAnalyzerMessages() {
        try {
            while (true) {
                receiveTestResultsFromAnalyzer(os, is);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Ensure the socket is closed when done
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendTestRequestsToAnalyzer(JsonObject sample) {
        try {
            if (socket == null || socket.isClosed()) {
                throw new IllegalStateException("Socket is not connected or has been closed.");
            }
            sendENQ(os, is);
            sendTestRequest(os, is, sample);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendENQ(OutputStream os, InputStream is) throws IOException {
        os.write(0x05); // ENQ
        os.flush();
        if (is.read() == 0x06) { // ACK
            System.out.println("Connection established with analyzer.");
        } else {
            System.out.println("Failed to establish connection with analyzer.");
        }
    }

    private static void sendTestRequest(OutputStream os, InputStream is, JsonObject sample) throws IOException {
        String message = buildTestRequestMessage(sample);
        os.write(0x02); // STX
        os.write(message.getBytes(StandardCharsets.UTF_8));
        os.write(0x03); // ETX
        os.flush();
        if (is.read() == 0x06) { // ACK
            System.out.println("Test request sent successfully.");
        } else {
            System.out.println("Failed to send test request.");
        }
        os.write(0x04); // EOT
        os.flush();
        if (is.read() == 0x06) { // ACK
            System.out.println("End of transmission acknowledged.");
        }
    }

    private static String buildTestRequestMessage(JsonObject sample) {
        // Construct the ASTM message for test request
        String sampleId = sample.get("sampleId").getAsString();
        JsonArray testCodes = sample.getAsJsonArray("testCodes");
        StringBuilder testCodeString = new StringBuilder();
        for (JsonElement code : testCodes) {
            testCodeString.append(code.getAsString()).append("^^^").append("|");
        }

        return "H|\\^&||PSWD|Maglumi User|||||Lis||P|E1394-97|20240730\r"
                + "P|1\r"
                + "O|1|" + sampleId + "||" + testCodeString.toString() + "\r"
                + "L|1|N\r";
    }

    public static void receiveTestResultsFromAnalyzer(OutputStream os, InputStream is) throws IOException {
        os.write(0x05); // ENQ
        os.flush();
        if (is.read() == 0x06) { // ACK
            System.out.println("Analyzer ready for results.");
            os.write(0x02); // STX
            os.flush();
            if (is.read() == 0x06) { // ACK
                byte[] buffer = new byte[1024];
                int bytesRead = is.read(buffer);
                String message = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                System.out.println("Received message: " + message);
                os.write(0x04); // EOT
                os.flush();
                if (is.read() == 0x06) { // ACK
                    System.out.println("End of transmission acknowledged.");

                    // Process and send results to LIS
                    JsonObject testResults = parseResults(message);
                    LisCommunicator.sendTestResultsToLIS(testResults);
                }
            }
        }
    }

    private static JsonObject parseResults(String message) {
        // Parse the received ASTM message and convert it into a JsonObject
        JsonObject resultJson = new JsonObject();
        JsonArray resultsArray = new JsonArray();

        // Assuming message is separated by newline and each line represents a result
        String[] lines = message.split("\r");
        for (String line : lines) {
            if (line.startsWith("R|")) { // Assuming 'R|' starts a result line in the ASTM message
                JsonObject result = new JsonObject();
                String[] parts = line.split("\\|");
                result.addProperty("testCode", parts[2].replace("^^^", "")); // Example parsing, adjust as needed
                result.addProperty("result", parts[3]);
                result.addProperty("units", parts[4]);
                result.addProperty("referenceRange", parts[5]);
                result.addProperty("resultStatus", parts[6]);
                resultsArray.add(result);
            }
        }
        resultJson.add("results", resultsArray);
        return resultJson;
    }
}

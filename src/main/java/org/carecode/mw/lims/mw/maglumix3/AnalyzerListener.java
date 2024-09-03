package org.carecode.mw.lims.mw.maglumix3;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AnalyzerListener implements Runnable {

    @Override
    public void run() {
        try {
            int analyzerPort = SettingsLoader.settings.getAsJsonObject("middlewareSettings")
                    .getAsJsonObject("analyzerDetails")
                    .get("analyzerPort").getAsInt();
            ServerSocket serverSocket = new ServerSocket(analyzerPort);

            while (true) {
                Socket socket = serverSocket.accept();
                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();

                receiveTestResultsFromAnalyzer(os, is);

                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void receiveTestResultsFromAnalyzer(OutputStream os, InputStream is) throws IOException {
        // Wait for ENQ
        int response = is.read();
        if (response == 0x05) { // ENQ
            os.write(0x06); // ACK
            os.flush();

            boolean receiving = true;
            StringBuilder messageBuilder = new StringBuilder();

            while (receiving) {
                // Read the STX
                response = is.read();
                if (response == 0x02) { // STX
                    byte[] buffer = new byte[1024];
                    int bytesRead = is.read(buffer);
                    String message = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    messageBuilder.append(message);

                    // Read the ETX
                    response = is.read();
                    if (response == 0x03) { // ETX
                        // Send ACK after receiving the message
                        os.write(0x06); // ACK
                        os.flush();
                    }
                } else if (response == 0x04) { // EOT
                    // End of Transmission
                    receiving = false;
                    os.write(0x06); // Final ACK
                    os.flush();
                }
            }

            String completeMessage = messageBuilder.toString();
            System.out.println("Received complete message: " + completeMessage);
            // Here you can process the complete message and send it to the LIS
            JsonObject testResults = parseResults(completeMessage);
            LisCommunicator.sendTestResultsToLIS(testResults);
        } else {
            System.out.println("Unexpected signal received.");
        }
    }

    private static JsonObject parseResults(String message) {
        // Parse the ASTM message and convert it into a JsonObject
        JsonObject results = new JsonObject();
        // Implementation of parsing logic goes here
        return results;
    }
}

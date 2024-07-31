package org.carecode.mw.lims.mw.indiko;

import java.io.OutputStream;
import java.net.Socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AnalyzerCommunicator {

    public static void sendRequestToAnalyzer(String sampleId, JsonArray tests) {
        try {
            JsonObject tcpipSettings = SettingsLoader.settings.getAsJsonObject("middlewareSettings").getAsJsonObject("tcpipSettings");
            String analyzerIP = tcpipSettings.get("analyzerAddress").getAsString();
            int analyzerPort = tcpipSettings.get("analyzerPort").getAsInt();

            Socket socket = new Socket(analyzerIP, analyzerPort);
            OutputStream out = socket.getOutputStream();

            JsonObject request = new JsonObject();
            request.addProperty("sampleId", sampleId);
            request.add("tests", tests);

            out.write(request.toString().getBytes());
            out.flush();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonObject getResultsFromAnalyzer() {
        // Implement the logic to retrieve results from the analyzer
        // This is a placeholder implementation and needs to be replaced with actual logic
        JsonObject results = new JsonObject();
        JsonArray sampleResults = new JsonArray();

        JsonObject sample1 = new JsonObject();
        sample1.addProperty("sampleId", "sample1");
        JsonObject testResults1 = new JsonObject();
        testResults1.addProperty("test1", "result1");
        testResults1.addProperty("test2", "result2");
        sample1.add("results", testResults1);

        JsonObject sample2 = new JsonObject();
        sample2.addProperty("sampleId", "sample2");
        JsonObject testResults2 = new JsonObject();
        testResults2.addProperty("test1", "result3");
        testResults2.addProperty("test2", "result4");
        sample2.add("results", testResults2);

        sampleResults.add(sample1);
        sampleResults.add(sample2);

        results.add("samples", sampleResults);
        return results;
    }
}

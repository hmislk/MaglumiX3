package org.carecode.mw.lims.mw.indiko;

public class Indiko {

    public static void main(String[] args) {
        System.out.println("MDGPHM");
        SettingsLoader.loadSettings();

        // Start the server in a separate thread to listen for connections from the analyzer
        Thread serverThread = new Thread(() -> AnalyzerCommunicator.startServer());
        serverThread.start();

        // Keep the main thread alive to continue listening
        try {
            serverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

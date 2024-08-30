package org.carecode.mw.lims.mw.indiko;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.MiddlewareSettings;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.PatientDataBundle;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class Indiko {

    static boolean testingPullingTestOrders = false;
    static boolean testingPushingTestResults = false;
    public static MiddlewareSettings middlewareSettings;

    public static final Logger logger = LogManager.getLogger(Indiko.class);

    public static void main(String[] args) {
        if (testingPullingTestOrders) {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
            QueryRecord queryRecord = new QueryRecord(0, "101010", null, null);
            logger.info("queryRecord=" + queryRecord);
            DataBundle pb = LISCommunicator.pullTestOrdersForSampleRequests(queryRecord);
            logger.info("pb = " + pb);
            System.exit(0);
        } else if (testingPushingTestResults) {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");

            // Simulated patient data
            DataBundle pdb = new DataBundle();
            pdb.setMiddlewareSettings(Indiko.middlewareSettings);
            PatientRecord patientRecord = new PatientRecord(0, "1212", "1212", "Buddhika", "Ari", "Male", "Sinhalese", "19750914", "Galle", "0715812399", "Niluka Gunasekara");
            pdb.setPatientRecord(patientRecord);

            // Results with bogus values
            ResultsRecord r1 = new ResultsRecord(
                    1, // frameNumber
                    "GLU", // testCode
                    112.0, // resultValue
                    5.0, // minimumValue
                    115.0, // maximumValue
                    "N", // flag
                    "Serum", // sampleType
                    "mg/dl", // resultUnits
                    "202408172147", // resultDateTime
                    "Indigo", // instrumentName
                    "1212" // sampleId
            );
            pdb.getResultsRecords().add(r1);

            // Additional bogus result for demonstration
            ResultsRecord r2 = new ResultsRecord(
                    2,
                    "CHOL",
                    200.0,
                    120.0,
                    240.0,
                    "H",
                    "Plasma",
                    "mg/dl",
                    "202408172200",
                    "Indigo",
                    "1213"
            );
            pdb.getResultsRecords().add(r2);

            // Query records as part of the demonstration
            QueryRecord qr = new QueryRecord(0, "1101", "1101", "");
            pdb.getQueryRecords().add(qr);

            // Communicate with LIS
            LISCommunicator.pushResults(pdb);

            System.exit(0);
        }
        logger.info("Starting Indiko middleware...");
        try {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load settings.", e);
            return;
        }

        int port = Indiko.middlewareSettings.getAnalyzerDetails().getAnalyzerPort();
        IndikoServer server = new IndikoServer();
        server.start(port);
    }

}

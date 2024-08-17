package org.carecode.mw.lims.mw.indiko;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.PatientDataBundle;
import org.carecode.lims.libraries.QueryRecord;

public class Indiko {

    static boolean testing=true;
    
    public static final Logger logger = LogManager.getLogger(Indiko.class);

    public static void main(String[] args) {
        if(testing){
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
            QueryRecord queryRecord = new QueryRecord(0, "101010", null, null);
            logger.info("queryRecord="+queryRecord);
            PatientDataBundle pb = LISCommunicator.pullTestOrdersForSampleRequests(queryRecord);
            logger.info("pb = " + pb);
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

        int port = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();
        IndikoServer server = new IndikoServer();
        server.start(port);
    }
    
    
    
    
}

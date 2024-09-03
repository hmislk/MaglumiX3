package org.carecode.mw.lims.mw.MaglumiX3;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.OBR;
import ca.uhn.hl7v2.model.v25.segment.OBX;
import java.util.ArrayList;
import java.util.logging.Level;

public class MaglumiX3Server {

    private static final Logger logger = LogManager.getLogger(MaglumiX3Server.class);

    private static final char ENQ = 0x05;
    private static final char ACK = 0x06;
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char EOT = 0x04;
    private static final char CR = 0x0D;  // Carriage Return
    private static final char LF = 0x0A;  // Line Feed
    private static final char NAK = 0x15;
    private static final char NAN = 0x00; // Line Feed

    static String fieldD = "|";
    static String repeatD = Character.toString((char) 92);
    static String componentD = "^";
    static String escapeD = "&";

    boolean receivingQuery;
    boolean receivingResults;
    boolean respondingQuery;
    boolean respondingResults;
    boolean testing;
    boolean needToSendHeaderRecordForQuery;
    boolean needToSendPatientRecordForQuery;
    boolean needToSendOrderingRecordForQuery;
    boolean needToSendEotForRecordForQuery;

    private DataBundle patientDataBundle = new DataBundle();

    String patientId;
    static String sampleId;
    List<String> testNames;
    int frameNumber;
    char terminationCode = 'N';
    PatientRecord patientRecord;
    ResultsRecord resultRecord;
    OrderRecord orderRecord;
    QueryRecord queryRecord;

    private static final int START_OF_TEXT = 2;  // ASCII value for STX
    private static final int END_OF_TEXT = 3;    // ASCII value for ETX
    private static final int END_OF_TRANSMISSION = 4; // ASCII value for EOT
    private static final int ENQUIRY = 5;        // ASCII value for ENQ
    private static final int ACKNOWLEDGEMENT = 6; // ASCII value for ACK
    private static final int CARRIAGE_RETURN = 13; // ASCII value for CR, add if needed

    private ServerSocket serverSocket;

    private void handleClient(Socket clientSocket) {
        try (InputStream in = new BufferedInputStream(clientSocket.getInputStream()); OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {
            boolean sessionActive = true;
            StringBuilder messageBuilder = new StringBuilder();

            while (sessionActive) {
                System.out.println("Waiting for data...");
                int data = in.read();
                System.out.println("Data received: " + data + " (char: " + (char) data + ")");

                if (data == -1) { // Client has closed the connection
                    sessionActive = false;
                    continue;
                }

                switch (data) {
                    case ENQUIRY:
                        System.out.println("Received ENQ");
                        out.write(ACKNOWLEDGEMENT);
                        out.flush();
                        System.out.println("Sent ACK");
                        break;
                    case START_OF_TEXT:
                        messageBuilder = new StringBuilder(); // Reset the StringBuilder for a new message
                        System.out.println("Start of text detected");
                        break;
                    case END_OF_TEXT:
                    case CARRIAGE_RETURN: // Handle CR as potential end of message
                        String message = messageBuilder.toString();
                        System.out.println("Complete HL7 message received: " + message);
                        processHL7Messages(message);//this is important
                        out.write(ACKNOWLEDGEMENT);
                        out.flush();
                        System.out.println("Sent ACK after message processing");
                        break;
                    case END_OF_TRANSMISSION:
                        System.out.println("EOT Received, closing connection");
                        sessionActive = false; // End the session as EOT is received
                        break;
                    default:
                        if (data != START_OF_TEXT && data != END_OF_TRANSMISSION) {
                            messageBuilder.append((char) data); // Build the HL7 message
                            System.out.println("Building message: " + messageBuilder);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error during communication with client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client socket closed");
            } catch (IOException e) {
                System.err.println("Error while closing the client socket: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Process the individual HL7 messages received
    
    public void processHL7Messages(String combinedMessages) {
        String[] individualMessages = splitHL7Messages(combinedMessages);
        for (String message : individualMessages) {
            if (!message.trim().isEmpty()) {
                processIndividualHL7Message(message);
            }
        }
    }

    // Split the combined string of HL7 messages into individual messages
    private String[] splitHL7Messages(String combinedMessages) {
        return combinedMessages.split("(?<=\\r)\\n");
    }

    // Process each individual HL7 message and determine action based on message type
    private void processIndividualHL7Message(String message) {
        HapiContext context = new DefaultHapiContext();
        Parser parser = context.getPipeParser();

        try {
            Message hl7Message = parser.parse(message);
            MSH msh = (MSH) hl7Message.get("MSH");
            String messageType = msh.getMessageType().getMessageCode().getValue();

            switch (messageType) {
                case "OUL^R22":
                    handleResultMessage(hl7Message);
                    break;
                case "QRY^R02":
                    handleQueryMessage(hl7Message);
                    break;
                default:
                    System.err.println("Unhandled message type: " + messageType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing individual HL7 message: " + e.getMessage());
        } finally {
            try {
                context.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(MaglumiX3Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    // Create a QueryRecord from a parsed HL7 message
    private void handleQueryMessage(Message hl7Message) {
        // Assume we extract sampleId and create a QueryRecord accordingly
        String sampleId = extractSampleId(hl7Message);  // Implement this method based on your message structure
        QueryRecord qr = new QueryRecord(0, sampleId, "universalTestId", "queryType");
        LISCommunicator.pullTestOrdersForSampleRequests(queryRecord);
    }

    public String extractSampleId(Message hl7Message) {
        try {
            // Extracting the MSH segment to access message details
            MSH msh = (MSH) hl7Message.get("MSH");

            // We will manually parse the field from the MSH segment since QPD-like data is actually here
            String messageType = msh.getMessageType().getMessageCode().getValue();
            if (!"TSREQ".equals(messageType)) {
                throw new IllegalArgumentException("Unsupported message type for sample ID extraction: " + messageType);
            }

            // The field index might vary; you need to confirm the index from a sample message
            // Assuming the structure of the QPD-like data is in MSH-9 field
            String fieldContent = msh.getField(9, 0).encode();

            // Assuming the structure is `215698^^NA003^1` and we want the first part before the first '^'
            String[] parts = fieldContent.split("\\^");
            if (parts.length > 0) {
                return parts[0];  // Returns '215698' as the sample ID
            } else {
                return "Sample ID not found";  // Handling case where no data is available
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error extracting sample ID: " + e.getMessage();
        }
    }

//    private void handleResultMessage(Message hl7Message) {
//        ORU_R01 message = (ORU_R01) hl7Message;
//        DataBundle dataBundle = new DataBundle();
//        dataBundle.setMiddlewareSettings(SettingsLoader.getSettings());
//
//        // Patient information
//        PID pid = message.getPATIENT_RESULT().getPATIENT().getPID();
//        String patientId = pid.getPatientIdentifierList(0).getIDNumber().getValue();
//        String additionalId = pid.getPatientIdentifierList(0).getIdentifierTypeCode().getValue();
//        String patientName = pid.getPatientName(0).getFamilyName().getSurname().getValue();
//        String patientSecondName = pid.getPatientName(0).getGivenName().getValue();
//        String patientSex = pid.getAdministrativeSex().getValue();
//        String race = pid.getRace(0).getIdentifier().getValue();
//        String dob = "";
//        String patientAddress = pid.getPatientAddress(0).getStreetAddress().getStreetOrMailingAddress().getValue();
//        String patientPhoneNumber = "";
//        String attendingDoctor = message.getPATIENT_RESULT().getPATIENT().getVISIT().getPV1().getAttendingDoctor(0).getFamilyName().getSurname().getValue();
//
//        PatientRecord pr = new PatientRecord(0,
//                patientId,
//                additionalId,
//                patientName,
//                patientSecondName,
//                patientSex,
//                race,
//                dob,
//                patientAddress,
//                patientPhoneNumber,
//                attendingDoctor);
//        dataBundle.setPatientRecord(pr);
//
//        // Iterate over all OBX segments within the first OBR segment
//        OBR obr = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR();
//        int obxCount = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATIONReps();
//
//        for (int i = 0; i < obxCount; i++) {
//            OBX obx = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION(i).getOBX();
//            String testCode = obx.getObservationIdentifier().getIdentifier().getValue();
//            String resultValueString = obx.getObservationValue(0).getData().toString();
//            String resultUnits = obx.getUnits().getIdentifier().getValue();
//            String resultDateTime = "";
//            String instrumentName = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerName();
//            String sampleId = obr.getFillerOrderNumber().getEntityIdentifier().getValue();
//
//            ResultsRecord rr = new ResultsRecord(testCode, resultValueString, resultUnits, resultDateTime, instrumentName, sampleId);
//
//            LISCommunicator.pushResults(patientDataBundle);
//
//            dataBundle.getResultsRecords().add(rr);
//        }
//
//        logger.info("Handling Result Message: " + hl7Message.toString());
//    }
    private void processHL7Message(String message) {
        logger.info("Processing HL7 Message: " + message);

        // Create a parser instance (HAPI library)
        Parser parser = new PipeParser();

        try {
            // Parse the message string into a Message object
            Message hl7Message = parser.parse(message);

            // Extract MSH segment
            Segment mshSegment = (Segment) hl7Message.get("MSH");

            // Extract message type and trigger event (e.g., ADT^A01)
            String messageType = mshSegment.getField(9, 0).toString();
            String triggerEvent = mshSegment.getField(10, 0).toString();

            // Log the extracted values
            logger.info("Message Type: " + messageType);
            logger.info("Trigger Event: " + triggerEvent);

            // Route based on type and event
            switch (messageType) {
                case "OUL^R22":
                    handleResultMessage(hl7Message);
                    break;
                // Add more cases as needed for other types
                default:
                    logger.warn("Unhandled message type: " + messageType);
            }
        } catch (Exception e) {
            logger.error("Failed to parse HL7 message", e);
        }
    }

    public List<QueryRecord> createQueryRecordsFromMessages(String combinedMessages) throws IOException {
        List<QueryRecord> records = new ArrayList<>();
        String[] individualMessages = combinedMessages.split("MSH");

        HapiContext context = new DefaultHapiContext();
        Parser parser = context.getPipeParser();

        try {
            for (String rawMessage : individualMessages) {
                if (!rawMessage.trim().isEmpty()) {
                    // Prepend "MSH" as it's removed by the split operation
                    String message = "MSH" + rawMessage;

                    try {
                        Message hl7Message = parser.parse(message);
                        // Manually navigate to the right segment and field to extract the sample ID
                        String sampleId = extractSampleId(hl7Message);
                        QueryRecord qr = new QueryRecord(0, sampleId, "universalTestId", "queryType");
                        records.add(qr);
                    } catch (Exception e) {
                        System.err.println("Error parsing message: " + e.getMessage());
                    }
                }
            }
        } finally {
            context.close();
        }

        return records;
    }

// Example handler method for result messages
    private void handleResultMessage(Message hl7Message) {
        ORU_R01 message = (ORU_R01) hl7Message;
        DataBundle dataBundle = new DataBundle();
        dataBundle.setMiddlewareSettings(SettingsLoader.getSettings());

        // Patient information
        PID pid = message.getPATIENT_RESULT().getPATIENT().getPID();
        String patientId = pid.getPatientIdentifierList(0).getIDNumber().getValue();
        String additionalId = pid.getPatientIdentifierList(0).getIdentifierTypeCode().getValue();
        String patientName = pid.getPatientName(0).getFamilyName().getSurname().getValue();
        String patientSecondName = pid.getPatientName(0).getGivenName().getValue();
        String patientSex = pid.getAdministrativeSex().getValue();
        String race = pid.getRace(0).getIdentifier().getValue();
        String dob = "";
        String patientAddress = pid.getPatientAddress(0).getStreetAddress().getStreetOrMailingAddress().getValue();
        String patientPhoneNumber = "";
        String attendingDoctor = message.getPATIENT_RESULT().getPATIENT().getVISIT().getPV1().getAttendingDoctor(0).getFamilyName().getSurname().getValue();

        PatientRecord pr = new PatientRecord(0,
                patientId,
                additionalId,
                patientName,
                patientSecondName,
                patientSex,
                race,
                dob,
                patientAddress,
                patientPhoneNumber,
                attendingDoctor);
        dataBundle.setPatientRecord(pr);

        // Iterate over all OBX segments within the first OBR segment
        OBR obr = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR();
        int obxCount = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATIONReps();

        for (int i = 0; i < obxCount; i++) {
            OBX obx = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION(i).getOBX();
            String testCode = obx.getObservationIdentifier().getIdentifier().getValue();
            String resultValueString = obx.getObservationValue(0).getData().toString();
            String resultUnits = obx.getUnits().getIdentifier().getValue();
            String resultDateTime = "";
            String instrumentName = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerName();
            String sampleId = obr.getFillerOrderNumber().getEntityIdentifier().getValue();

            ResultsRecord rr = new ResultsRecord(testCode, resultValueString, resultUnits, resultDateTime, instrumentName, sampleId);

            LISCommunicator.pushResults(patientDataBundle);

            dataBundle.getResultsRecords().add(rr);
        }

        logger.info("Handling Result Message: " + hl7Message.toString());
    }

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("Server started on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    logger.info("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                    handleClient(clientSocket);
                } catch (IOException e) {
                    logger.error("Error handling client connection", e);
                }
            }
        } catch (IOException e) {
            logger.error("Error starting server on port " + port, e);
        } finally {
            stop();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                logger.info("Server stopped.");
            }
        } catch (IOException e) {
            logger.error("Error stopping server", e);
        }
    }

    private void handleClientOld(Socket clientSocket) {
        try (InputStream in = new BufferedInputStream(clientSocket.getInputStream()); OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {
            StringBuilder asciiDebugInfo = new StringBuilder();
            boolean sessionActive = true;
            boolean inChecksum = false;
            int checksumCount = 0;

            while (sessionActive) {
                int data = in.read();

                if (inChecksum) {
                    asciiDebugInfo.append((char) data).append(" (").append(data).append(") ");  // Append character and its ASCII value
                    checksumCount++;
                    if (checksumCount == 4) {
                        inChecksum = false;
                        checksumCount = 0;
                        asciiDebugInfo.setLength(0);  // Clear the StringBuilder for the next use
                    }
                    continue;
                }

                switch (data) {
                    case ENQ:
                        logger.debug("Received ENQ");
                        out.write(ACK);
                        out.flush();
                        logger.debug("Sent ACK");
                        break;
                    case ACK:
                        logger.debug("ACK Received.");
                        handleAck(clientSocket, out);
                        break;
                    case STX:
                        inChecksum = true;
                        StringBuilder message = new StringBuilder();
                        asciiDebugInfo = new StringBuilder();  // To store ASCII values for debugging

                        while ((data = in.read()) != ETX) {
                            if (data == -1) {
                                break;
                            }
                            message.append((char) data);
                            asciiDebugInfo.append("[").append(data).append("] ");  // Append ASCII value in brackets
                        }
                        logger.debug("Message received: " + message);
                        processMessage(message.toString(), clientSocket);
                        out.write(ACK);
                        out.flush();
                        logger.debug("Sent ACK after STX-ETX block");
                        break;
                    case EOT:
                        logger.debug("EOT Received");
                        handleEot(out);
                        break;
                    default:
                        if (!inChecksum) {
                            logger.debug("Received unexpected data: " + (char) data + " (ASCII: " + data + ")");
                            asciiDebugInfo.append((char) data).append(" (").append(data).append(") ");
                        } else {
                            logger.debug("Data within checksum calculation: " + (char) data + " (ASCII: " + data + ")");
                        }
                        break;
                }
            }
        } catch (IOException e) {
            logger.error("Error during client communication", e);
        }
    }

    private void handleAck(Socket clientSocket, OutputStream out) throws IOException {
        System.out.println("handleAck = ");
        System.out.println("needToSendHeaderRecordForQuery = " + needToSendHeaderRecordForQuery);
        if (needToSendHeaderRecordForQuery) {
            logger.debug("Sending Header");
            String hm = createLimsHeaderRecord();
            sendResponse(hm, clientSocket);
            frameNumber = 2;
            needToSendHeaderRecordForQuery = false;
            needToSendPatientRecordForQuery = true;
        } else if (needToSendPatientRecordForQuery) {
            logger.debug("Creating Patient record ");
            patientRecord = getPatientDataBundle().getPatientRecord();
            if (patientRecord.getPatientName() == null) {
                patientRecord.setPatientName("Buddhika");
            }
            patientRecord.setFrameNumber(frameNumber);
            String pm = createLimsPatientRecord(patientRecord);
            sendResponse(pm, clientSocket);
            frameNumber = 3;
            needToSendPatientRecordForQuery = false;
            needToSendOrderingRecordForQuery = true;
        } else if (needToSendOrderingRecordForQuery) {
            logger.debug("Creating Order record ");
            if (testNames == null || testNames.isEmpty()) {
                testNames = Arrays.asList("Gluc GP");
            }
            orderRecord = getPatientDataBundle().getOrderRecords().get(0);
            orderRecord.setFrameNumber(frameNumber);
            String om = createLimsOrderRecord(orderRecord);
            sendResponse(om, clientSocket);
            frameNumber = 4;
            needToSendOrderingRecordForQuery = false;
            needToSendEotForRecordForQuery = true;
        } else if (needToSendEotForRecordForQuery) {
            System.out.println("Creating an End record = ");
            String tmq = createLimsTerminationRecord(frameNumber, terminationCode);
            sendResponse(tmq, clientSocket);
            needToSendEotForRecordForQuery = false;
            receivingQuery = false;
            receivingResults = false;
            respondingQuery = false;
            respondingResults = false;
        } else {
            out.write(EOT);
            out.flush();
            logger.debug("Sent EOT");
        }
    }

    private void sendResponse(String response, Socket clientSocket) {
        String astmMessage = buildASTMMessage(response);
        try {
            OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
            out.write(astmMessage.getBytes());
            out.flush();
            logger.debug("Response sent: " + response);
        } catch (IOException e) {
            logger.error("Failed to send response", e);
        }
    }

    private void handleEot(OutputStream out) throws IOException {
        logger.debug("Handling eot");
        logger.debug(respondingQuery);
        if (respondingQuery) {
            patientDataBundle = LISCommunicator.pullTestOrdersForSampleRequests(patientDataBundle.getQueryRecords().get(0));
            logger.debug("Starting Transmission to send test requests");
            out.write(ENQ);
            out.flush();
            logger.debug("Sent ENQ");
        } else if (respondingResults) {
            LISCommunicator.pushResults(patientDataBundle);
        } else {
            logger.debug("Received EOT, ending session");
        }
    }

    public static String calculateChecksum(String frame) {
        String checksum = "00";
        int sumOfChars = 0;
        boolean complete = false;

        for (int idx = 0; idx < frame.length(); idx++) {
            int byteVal = frame.charAt(idx);

            switch (byteVal) {
                case 0x02: // STX
                    sumOfChars = 0;
                    break;
                case 0x03: // ETX
                case 0x17: // ETB
                    sumOfChars += byteVal;
                    complete = true;
                    break;
                default:
                    sumOfChars += byteVal;
                    break;
            }

            if (complete) {
                break;
            }
        }

        if (sumOfChars > 0) {
            checksum = Integer.toHexString(sumOfChars % 256).toUpperCase();
            return (checksum.length() == 1 ? "0" + checksum : checksum);
        }

        return checksum;
    }

    public String createHeaderMessage() {
        String headerContent = "1H|^&|||1^CareCode^1.0|||||||P|";
        return headerContent;
    }

    public String buildASTMMessage(String content) {
        String msdWithStartAndEnd = STX + content + CR + ETX;
        String checksum = calculateChecksum(msdWithStartAndEnd);
        String completeMsg = msdWithStartAndEnd + checksum + CR + LF;
        return completeMsg;
    }

    public String createLimsPatientRecord(PatientRecord patient) {
        // Delimiter used in the ASTM protocol
        String delimiter = "|";

        // Construct the start of the patient record, including frame number
        String patientStart = patient.getFrameNumber() + "P" + delimiter;

        // Concatenate patient information fields with actual patient data
        String patientInfo = "1" + delimiter
                + // Sequence Number
                patient.getPatientId() + delimiter
                + // Patient ID
                delimiter
                + // [Empty field for additional ID]
                delimiter
                + // [Empty field for more data]
                patient.getPatientName() + delimiter
                + // Patient Name
                delimiter
                + // [Empty field for more patient data]
                "U" + delimiter
                + // Sex (assuming 'U' for unspecified)
                delimiter
                + // [Empty field]
                delimiter
                + // [More empty fields]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [And more...]
                delimiter
                + // [And more...]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [Continued empty fields]
                delimiter
                + // [Continued empty fields]
                patient.getAttendingDoctor() + delimiter;    // Attending Doctor

        // Construct the full patient record
        return patientStart + patientInfo;
    }

    public static String createLimsOrderRecord(int frameNumber, String sampleId, List<String> testNames, String specimenCode, Date collectionDate, String testInformation) {
        // Delimiter used in the ASTM protocol

        String delimiter = "|";

        // SimpleDateFormat to format the Date object to the required ASTM format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String formattedDate = dateFormat.format(collectionDate);

        // Construct each field individually
        String frameNumberAndRecordType = frameNumber + "O"; // Combining frame number and Record Type 'O' without a delimiter
        String sequenceNumber = "1";
        String sampleID = sampleId;
        String instrumentSpecimenID = ""; // Instrument Specimen ID (Blank)
        StringBuilder orderedTests = new StringBuilder();
        for (int i = 0; i < testNames.size(); i++) {
            orderedTests.append("^^^").append(testNames.get(i));
            if (i < testNames.size() - 1) {
                orderedTests.append("\\"); // Append backslash except after the last test name
            }
        }
        String specimenType = specimenCode;
        String fillField = ""; // Fill field (Blank)
        String dateTimeOfCollection = formattedDate;
        String priority = ""; // Priority (Blank)

        String physicianID = testInformation;
        String physicianName = ""; // Physician Name (Blank)
        String userFieldNo1 = ""; // User Field No. 1 (Blank)
        String userFieldNo2 = ""; // User Field No. 2 (Blank)
        String labFieldNo1 = ""; // Laboratory Field No. 1 (Blank)
        String labFieldNo2 = ""; // Laboratory Field No. 2 (Blank)
        String dateTimeSpecimenReceived = ""; // Date/Time specimen received in lab (Blank)
        String specimenDescriptor = ""; // Specimen descriptor (Blank)
        String orderingMD = ""; // Ordering MD (Blank)
        String locationDescription = ""; // Location description (Blank)
        String ward = ""; // Ward (Blank)
        String invoiceNumber = ""; // Invoice Number (Blank)
        String reportType = ""; // Report type (Blank)
        String reservedField1 = ""; // Reserved Field (Blank)
        String reservedField2 = ""; // Reserved Field (Blank)
        String transportInformation = ""; // Transport information (Blank)

        // Concatenate all fields with delimiters
        return frameNumberAndRecordType + delimiter
                + sequenceNumber + delimiter
                + sampleID + delimiter
                + instrumentSpecimenID + delimiter
                + orderedTests + delimiter;
//                + specimenType + delimiter
//                + fillField + delimiter
//                + dateTimeOfCollection + delimiter
//                + priority + delimiter
//                
//                + physicianID + delimiter
//                + physicianName + delimiter
//                + userFieldNo1 + delimiter
//                + userFieldNo2 + delimiter
//                + labFieldNo1 + delimiter
//                + labFieldNo2 + delimiter
//                + dateTimeSpecimenReceived + delimiter
//                + specimenDescriptor + delimiter
//                + orderingMD + delimiter
//                + locationDescription + delimiter
//                + ward + delimiter
//                + invoiceNumber + delimiter
//                + reportType + delimiter
//                + reservedField1 + delimiter
//                + reservedField2 + delimiter
//                + transportInformation;
    }

    public String createLimsHeaderRecord() {
        String analyzerNumber = "1";
        String analyzerName = "LIS host";
        String databaseVersion = "1.0";
        String hr1 = "1H";
        String hr2 = fieldD + repeatD + componentD + escapeD;
        String hr3 = "";
        String hr4 = "";
        String hr5 = analyzerNumber + componentD + analyzerName + componentD + databaseVersion;
        String hr6 = "";
        String hr7 = "";
        String hr8 = "";
        String hr9 = "";
        String hr10 = "";
        String hr11 = "";
        String hr12 = "P";
        String hr13 = "";
        String hr14 = "20240508221500";
        String header = hr1 + hr2 + fieldD + hr3 + fieldD + hr4 + fieldD + hr5 + fieldD + hr6 + fieldD + hr7 + fieldD + hr8 + fieldD + hr9 + fieldD + hr10 + fieldD + hr11 + fieldD + hr12 + fieldD + hr13 + fieldD + hr14;

        header = hr1 + hr2 + fieldD + hr3 + fieldD + hr4 + fieldD + hr5 + fieldD + hr6 + fieldD + hr7 + fieldD + hr8 + fieldD + hr9 + fieldD + hr10 + fieldD + hr11 + fieldD + hr12;

        return header;

    }

    public String createLimsOrderRecord(OrderRecord order) {
        return createLimsOrderRecord(order.getFrameNumber(), order.getSampleId(), order.getTestNames(), order.getSpecimenCode(), order.getOrderDateTime(), order.getTestInformation());
    }

    public String createLimsTerminationRecord(int frameNumber, char terminationCode) {
        String delimiter = "|";
        String terminationStart = frameNumber + "L" + delimiter;
        String terminationInfo = "1" + delimiter + terminationCode; // '1' is the record number, usually fixed
        return terminationStart + terminationInfo;
    }

    private void processMessage(String data, Socket clientSocket) {

        if (data.length() >= 3 && Character.isDigit(data.charAt(0)) && data.charAt(2) == '|') {
            char recordType = data.charAt(1);

            switch (recordType) {
                case 'H': // Header Record
                    patientDataBundle = new DataBundle();
                    receivingQuery = false;
                    receivingResults = false;
                    respondingQuery = false;
                    respondingResults = false;
                    needToSendEotForRecordForQuery = false;
                    needToSendOrderingRecordForQuery = false;
                    needToSendPatientRecordForQuery = false;
                    needToSendHeaderRecordForQuery = false;
                    logger.debug("Header Record Received: " + data);
                    break;
                case 'R': // Result Record
                    logger.debug("Result Record Received: " + data);
                    respondingResults = true;
                    respondingQuery = false;
                    resultRecord = parseResultsRecord(data);
                    getPatientDataBundle().getResultsRecords().add(resultRecord);
                    logger.debug("Result Record Parsed: " + resultRecord);
                    break;
                case 'Q': // Query Record
                    System.out.println("Query result received" + data);
                    receivingQuery = false;

                    respondingQuery = true;
                    needToSendHeaderRecordForQuery = true;
                    logger.debug("Query Record Received: " + data);
                    queryRecord = parseQueryRecord(data);
                    getPatientDataBundle().getQueryRecords().add(queryRecord);
                    logger.debug("Parsed the Query Record: " + queryRecord);
                    break;
                case 'P': // Patient Record
                    logger.debug("Patient Record Received: " + data);
                    patientRecord = parsePatientRecord(data);
                    getPatientDataBundle().setPatientRecord(patientRecord);
                    logger.debug("Patient Record Parsed: " + patientRecord);
                    break;
                case 'L': // Termination Record
                    logger.debug("Termination Record Received: " + data);
                    break;
                case 'C': // Comment Record
                    logger.debug("Comment Record Received: " + data);

                    break;
                case 'O': // Order Record or other type represented by 'O'
                    System.out.println("Order result received" + data);
                    logger.debug("Query Record Received: " + data);
                    String tmpSampleId = extractSampleIdFromOrderRecord(data);
                    System.out.println("tmpSampleId = " + tmpSampleId);
                    sampleId = tmpSampleId;
                    QueryRecord qr = new QueryRecord(0, sampleId, sampleId, "");
                    getPatientDataBundle().getQueryRecords().add(qr);
//                    OrderRecord orderRecord = new OrderRecord(2, sampleId, null, sampleId, "", "");
//                    getPatientDataBundle().getOrderRecords().add(orderRecord);
                    logger.debug("Parsed the Query Record: " + queryRecord);
                    break;
                default: // Unknown Record
                    logger.debug("Unknown Record Received: " + data);
                    break;
            }
        } else {
            logger.debug("Invalid Record Structure: " + data);
        }
    }

    public static PatientRecord parsePatientRecord(String patientSegment) {
        String[] fields = patientSegment.split("\\|");
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));
        String patientId = fields[1];
        String additionalId = fields[3]; // assuming index 2 is always empty as per your example
        String patientName = fields[4];
        String patientSecondName = fields[6]; // assuming this follows the same unused pattern
        String patientSex = fields[7];
        String race = ""; // Not available in the segment
        String dob = ""; // Date of birth, not available in the segment
        String patientAddress = fields[11];
        String patientPhoneNumber = fields[14];
        String attendingDoctor = fields[15];

        // Return a new PatientRecord object using the extracted data
        return new PatientRecord(
                frameNumber,
                patientId,
                additionalId,
                patientName,
                patientSecondName,
                patientSex,
                race,
                dob,
                patientAddress,
                patientPhoneNumber,
                attendingDoctor
        );
    }

    public static ResultsRecord parseResultsRecord(String resultSegment) {
        // Split the segment into fields
        String[] fields = resultSegment.split("\\|");

        // Ensure that the fields array has the expected length
        if (fields.length < 6) {
            logger.error("Insufficient fields in the result segment: {}", resultSegment);
            return null; // or throw an exception
        }

        // Extract the frame number by removing non-numeric characters
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));
        logger.debug("Frame number extracted: {}", frameNumber);

        // Test code should be correctly identified assuming it's provided correctly in the fields[1]
        String testCode = fields[2].split("\\^")[3]; // Extracting the correct part of the test code
        logger.debug("Test code extracted: {}", testCode);

        // Result value parsing assumes the result is in the fourth field
        String resultValue = fields[3];

        // Units and other details
        String resultUnits = fields[4];
        logger.debug("Result units extracted: {}", resultUnits);
        String resultDateTime = fields[12];
        logger.debug("Result date-time extracted: {}", resultDateTime);
        String instrumentName = fields[13];
        logger.debug("Instrument name extracted: {}", instrumentName);
        System.out.println("sampleId = " + sampleId);
        // Return a new ResultsRecord object initialized with extracted values
        return new ResultsRecord(
                frameNumber,
                testCode,
                resultValue,
                resultUnits,
                resultDateTime,
                instrumentName,
                sampleId
        );
    }

    public static OrderRecord parseOrderRecord(String orderSegment) {

        String[] fields = orderSegment.split("\\|");

        // Extract frame number and remove non-numeric characters (<STX>, etc.)
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));

        // Sample ID and associated data
        String[] sampleDetails = fields[1].split("\\^");
        String sampleId = sampleDetails[1]; // Adjust index based on your specific message structure

        // Extract test names, assuming they are separated by '^' inside a field like ^^^test1^test2
        List<String> testNames = Arrays.stream(fields[2].split("\\^"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // Specimen code
        String specimenCode = fields[3];

        // Order date and time
        String orderDateTime = fields[4];

        // Test information
        String testInformation = fields[6]; // Assuming test information is in the 7th segment

        // Return a new OrderRecord object using the extracted data
        return new OrderRecord(
                frameNumber,
                sampleId,
                testNames,
                specimenCode,
                orderDateTime,
                testInformation
        );
    }

    public static String extractSampleIdFromQueryRecord(String astm2Message) {
        // Step 1: Discard everything before "Q|"
        int startIndex = astm2Message.indexOf("Q|");
        if (startIndex == -1) {
            return null; // "Q|" not found in the message
        }
        String postQ = astm2Message.substring(startIndex);

        // Step 2: Get the string between the second and third "|"
        String[] fields = postQ.split("\\|");
        if (fields.length < 3) {
            return null; // Not enough fields
        }
        String secondField = fields[2]; // Get the field after the second "|"

        // Step 3: Get the string between the first and second "^"
        String[] sampleDetails = secondField.split("\\^");
        if (sampleDetails.length < 2) {
            return null; // Not enough data within the field
        }
        return sampleDetails[1]; // This should be the sample ID
    }

    public static String extractSampleIdFromOrderRecord(String astm2Message) {
        // Split the message by the '|' delimiter
        String[] fields = astm2Message.split("\\|");

        // Assuming the sample ID is in the third field (index 2)
        if (fields.length > 2) {
            // Extract the sample ID field (third field)
            String tmpSampleId = fields[2];

            // Split the tmpSampleId by '^' and return the first part
            String[] sampleIdParts = tmpSampleId.split("\\^");
            return sampleIdParts[0]; // Return only the part before the first '^'
        } else {
            return null; // or throw an exception if you prefer
        }
    }

    public static QueryRecord parseQueryRecord(String querySegment) {
        System.out.println("querySegment = " + querySegment);
        String tmpSampleId = extractSampleIdFromQueryRecord(querySegment);
        System.out.println("tmpSampleId = " + tmpSampleId);
        sampleId = tmpSampleId;
        System.out.println("Sample ID: " + tmpSampleId); // Debugging
        return new QueryRecord(
                0,
                tmpSampleId,
                "",
                ""
        );
    }

    public DataBundle getPatientDataBundle() {
        if (patientDataBundle == null) {
            patientDataBundle = new DataBundle();
        }
        return patientDataBundle;
    }

    public void setPatientDataBundle(DataBundle patientDataBundle) {
        this.patientDataBundle = patientDataBundle;
    }

}

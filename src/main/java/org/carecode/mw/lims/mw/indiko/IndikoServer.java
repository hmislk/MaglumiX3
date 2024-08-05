package org.carecode.mw.lims.mw.indiko;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IndikoServer {

    private static final Logger logger = LogManager.getLogger(IndikoServer.class);

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

    PatientDataBundle patientDataBundle = new PatientDataBundle();

    String patientId;
    String sampleId;
    String patientName;
    String referringDocName;
    List<String> testNames;
    String specimanCode = "S";
    String testInformation = "";
    int frameNumber;
    String limsName;
    String senderId;
    String processingId;
    String versionNumber;
    String patientSex;
    Date sampledDate;
    char terminationCode = 'N';
    PatientRecord patientRecord;
    ResultsRecord resultRecord;
    OrderRecord orderRecord;
    QueryRecord queryRecord;

    private ServerSocket serverSocket;

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

    private void handleClient(Socket clientSocket) {
        try (InputStream in = new BufferedInputStream(clientSocket.getInputStream()); OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {

            boolean sessionActive = true;
            boolean inChecksum = false;
            int checksumCount = 0;

            while (sessionActive) {
                int data = in.read();

                if (inChecksum) {
                    logger.debug("Checksum or trailing character: " + (char) data + " (ASCII: " + data + ")");
                    checksumCount++;
                    if (checksumCount == 4) {
                        inChecksum = false;
                        checksumCount = 0;
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
                        while ((data = in.read()) != ETX) {
                            if (data == -1) {
                                break;
                            }
                            message.append((char) data);
                        }
                        logger.debug("Complete message received: " + message);
                        processMessage(message.toString(), clientSocket);
                        out.write(ACK);
                        out.flush();
                        logger.debug("Sent ACK after STX-ETX block");
                        break;
                    case EOT:
                        logger.debug("EOT Received");
                        handleEot(out);
//                        sessionActive = false;
                        break;
                    default:
                        if (!inChecksum) {
                            logger.debug("Received unexpected data: " + (char) data + " (ASCII: " + data + ")");
                        }
                        break;
                }
            }
        } catch (IOException e) {
            logger.error("Error during client communication", e);
        }
    }

    private void handleAck(Socket clientSocket, OutputStream out) throws IOException {
        if (needToSendHeaderRecordForQuery) {
            logger.debug("Sending Header");
            String hm = createLimsHeaderRecord();
            String checksum = calculateChecksum(hm);
            logger.debug(hm);
            String shm = buildMessageWithChecksum(hm);
            logger.debug(shm);
            sendResponse(shm, clientSocket);

            frameNumber = 2;
            needToSendHeaderRecordForQuery = false;
            needToSendPatientRecordForQuery = true;
            logger.debug("Sent " + shm);
        } else if (needToSendPatientRecordForQuery) {
            logger.debug("Creating Patient record ");
            patientRecord = patientDataBundle.getPatientRecord();
            if (patientRecord.getPatientName() == null) {
                patientRecord.setPatientName("Buddhika");
            }
            patientRecord.setFrameNumber(frameNumber);
            String pm = createLimsPatientRecord(patientRecord);
            logger.debug("Sent " + pm);
            String checksum = calculateChecksum(pm);
            String spm = buildMessageWithChecksum(pm);
            sendResponse(spm, clientSocket);
            frameNumber = 3;
            needToSendPatientRecordForQuery = false;
            needToSendOrderingRecordForQuery = true;
            logger.debug("Sent " + spm);
        } else if (needToSendOrderingRecordForQuery) {
            if (testNames == null || testNames.isEmpty()) {
                testNames = Arrays.asList("Gluc GP");
            }
            orderRecord = patientDataBundle.getOrderRecords().get(0);
            orderRecord.setFrameNumber(frameNumber);
            String om = createLimsOrderRecord(orderRecord);
            String checksum = calculateChecksum(om);
            String som = buildMessageWithChecksum(om);
            sendResponse(som, clientSocket);
            frameNumber = 4;
            needToSendOrderingRecordForQuery = false;
            needToSendEotForRecordForQuery = true;
            logger.debug("Sent " + som);
        } else if (needToSendEotForRecordForQuery) {
            String tmq = createLimsTerminationRecord(frameNumber, terminationCode);
            String checksum = calculateChecksum(tmq);
            String qtmq = buildMessageWithChecksum(tmq);
            sendResponse(qtmq, clientSocket);
            needToSendEotForRecordForQuery = false;
            receivingQuery = false;
            receivingResults = false;
            respondingQuery = false;
            respondingResults = false;
            logger.debug("Sent " + qtmq);
        } else {
            out.write(EOT);
            out.flush();
            logger.debug("Sent EOT");
        }
    }

    private void sendResponse(String response, Socket clientSocket) {
        try {
            OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
            out.write(response.getBytes());
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

    public String calculateChecksum(String input) {
        int checksumValue = 0;
        for (char c : input.toCharArray()) {
            checksumValue += (int) c;
        }
        checksumValue %= 256;
        return String.format("%02X", checksumValue);
    }

    public String buildMessageWithChecksum(String message) {
        // Include STX and ETX in the message for checksum calculation
        String fullMessage = STX + message + ETX;
        String checksum = calculateChecksum(message);  // Calculate checksum on the message without STX and ETX
        return fullMessage + checksum + CR + LF;  // Append checksum and termination characters
    }

    public String createHeaderMessage() {
        String headerContent = "1H|^&|||1^LIS host^1.0|||||||P|";
        return buildMessageWithChecksum(headerContent);
    }


    public void sendToAnalyzer(String message, OutputStream outputStream) throws IOException {
        outputStream.write(message.getBytes());
        outputStream.flush();
    }
//  
//    private void handleClientTest(Socket clientSocket) {
//
//        try (InputStream in = new BufferedInputStream(clientSocket.getInputStream()); OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {
//
//            out.write(ENQ);
//            out.flush();
//
//            logger.debug("ENQ sent");
//            testing = true;
//
//            boolean sessionActive = true;
//            boolean inChecksum = false; // Flag to determine if we are currently reading checksum characters
//            int checksumCount = 0; // Counter for the checksum characters
//
//            while (sessionActive) {
//                int data = in.read();
//
//                if (inChecksum) {
//                    // Log both the character and its ASCII value for clarity
//                    logger.debug("Checksum or trailing character: " + (char) data + " (ASCII: " + data + ")");
//                    checksumCount++;
//                    if (checksumCount == 4) { // After reading two checksum characters, and CR, LF reset
//                        inChecksum = false;
//                        checksumCount = 0;
//                    }
//                    continue;
//                }
//
//                switch (data) {
//                    case ENQ:
//                        logger.debug("Received ENQ");
//                        out.write(ACK);
//                        out.flush();
//                        logger.debug("Sent ACK");
//                        break;
//                    case NAK:
//                        logger.debug("NAK Received. Terminating");
//                        System.exit(0);
//                    case ACK:
//                        logger.debug("ACK Received.");
//                        if (testing) {
//                            String hm = createLimsHeaderRecord();
//                            String checksum = calculateChecksum(hm);
//                            logger.debug(hm);
//                            String shm = buildMessageWithChecksum(hm,checksum);
//                            logger.debug(shm);
//                            sendResponse(shm, clientSocket);
//                            if (frameNumber > 1) {
//                                logger.debug("Header Sending Finished. Terminating");
//                                System.exit(0);
//                            }
//                            frameNumber = 2;
//                            logger.debug("Sent " + shm);
//                        } else if (needToSendHeaderRecordForQuery) {
//                            logger.debug("Sending Header");
//                            limsName = "CareCode";
//                            senderId = "1101";
//                            processingId = "22";
//                            versionNumber = "33";
//                            String hm = createExactLimsHeader();
//                            //(limsName, senderId, processingId, versionNumber);
//                            logger.debug(hm);
//                            String checksum = calculateChecksum(hm);
//                            String shm = buildMessageWithChecksum(hm,checksum);
//                            logger.debug(shm);
//                            sendResponse(shm, clientSocket);
//                            frameNumber = 2;
//                            needToSendHeaderRecordForQuery = false;
//                            needToSendPatientRecordForQuery = true;
//                            logger.debug("Sent " + shm);
//                        } else if (needToSendPatientRecordForQuery) {
//                            logger.debug("Creating Patient record ");
//                            patientRecord = patientDataBundle.getPatientRecord();
//                            if (patientRecord.getPatientName() == null) {
//                                patientRecord.setPatientName("Buddhika");
//                            }
//                            patientRecord.setFrameNumber(frameNumber);
//                            String pm = createLimsPatientRecord(patientRecord);
//                            logger.debug("Sent " + pm);
//                            String checksum = calculateChecksum(pm);
//                            String spm = buildMessageWithChecksum(pm,checksum);
//                            sendResponse(spm, clientSocket);
//                            frameNumber = 3;
//                            needToSendPatientRecordForQuery = false;
//                            needToSendOrderingRecordForQuery = true;
//                            logger.debug("Sent " + spm);
//                        } else if (needToSendOrderingRecordForQuery) {
//                            if (testNames == null || testNames.isEmpty()) {
//                                testNames = Arrays.asList("Gluc GP");
//                            }
//                            orderRecord = patientDataBundle.getOrderRecords().get(0);
//                            orderRecord.setFrameNumber(frameNumber);
//                            String om = createLimsOrderRecord(orderRecord);
//                            String checksum = calculateChecksum(om);
//                            String som = buildMessageWithChecksum(om, checksum);
//                            sendResponse(som, clientSocket);
//                            frameNumber = 4;
//                            needToSendOrderingRecordForQuery = false;
//                            needToSendEotForRecordForQuery = true;
//                            logger.debug("Sent " + som);
//                        } else if (needToSendEotForRecordForQuery) {
//                            String tmq = createLimsTerminationRecord(frameNumber, terminationCode);
//                            String checksum = calculateChecksum(tmq);
//                            String qtmq = buildMessageWithChecksum(tmq, checksum);
//                            sendResponse(qtmq, clientSocket);
//                            needToSendEotForRecordForQuery = false;
//                            receivingQuery = false;
//                            receivingResults = false;
//                            respondingQuery = false;
//                            respondingResults = false;
//                            logger.debug("Sent " + qtmq);
//                        } else {
//                            out.write(EOT);
//                            out.flush();
//                            logger.debug("Sent EOT");
//                        }
//                        break;
//                    case STX:
//                        inChecksum = true; // Set to start checksum detection
//                        StringBuilder message = new StringBuilder();
//                        while ((data = in.read()) != ETX) {
//                            if (data == -1) {
//                                break;
//                            }
//                            message.append((char) data);
//                        }
//                        logger.debug("Complete message received: " + message);
//                        processMessage(message.toString(), clientSocket);
//                        out.write(ACK);
//                        out.flush();
//                        logger.debug("Sent ACK after STX-ETX block");
//                        break;
//                    case EOT:
//                        logger.debug("EOT Received");
//                        if (respondingQuery) {
//                            logger.debug("Starting Transmission to send test requests");
//                            out.write(ENQ);
//                            out.flush();
//                            logger.debug("Sent ENQ");
//                            break;
//                        } else if (respondingResults) {
//                            LISCommunicator.pushResults(patientDataBundle);
//                        } else {
//                            sessionActive = false;
//                            logger.debug("Received EOT, ending session");
//                        }
//                        break;
//                    default:
//                        if (!inChecksum) { // Ignore unexpected data if not in checksum reading mode
//                            logger.debug("Received unexpected data: " + (char) data + " (ASCII: " + data + ")");
//                        }
//                        break;
//                }
//            }
//        } catch (IOException e) {
//            logger.error("Error during client communication", e);
//        }
//    }

    public String createExactLimsHeader() {
        // Initialize StringBuilder for assembling the header
        StringBuilder header = new StringBuilder();

        // Building the exact header string as specified
        header.append("1H|^&|||1^LIS host^1.0||||||P|");

        // Convert StringBuilder to String and return
        return header.toString();
    }

    public String createLimsPatientRecord(PatientRecord patient) {
        // Delimiter used in the ASTM protocol
        String delimiter = "|";

        // Start of patient segment
        String patientStart = patient.getFrameNumber() + "P" + delimiter;

        // Patient Information
        // Using data directly from the PatientRecord object
        String patientInfo = patient.getPatientId() + delimiter
                + delimiter // Placeholder for optional second ID
                + patient.getPatientName() + delimiter
                + delimiter // Placeholder for patient's second name if applicable
                + delimiter // Placeholder for patient's DOB (Date of Birth)
                + patient.getPatientSex() + delimiter
                + delimiter + delimiter // Placeholders for race and patient's address
                + delimiter + delimiter // Placeholders for reserved fields
                + delimiter + delimiter // Placeholders for phone number and attending physician's ID
                + patient.getAttendingDoctor() + delimiter;

        return patientStart + patientInfo;
    }

    public String createLimsOrderRecord(int frameNumber, String sampleId, List<String> testNames, String specimenCode, Date collectionDate, String testInformation) {
        // Delimiter used in the ASTM protocol
        String delimiter = "|";
        String caret = "^";
        String componentDelimiter = "\\^"; // Escape character for regular expression

        // SimpleDateFormat to format the Date object to the required ASTM format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

        // Format the date
        String formattedDate = dateFormat.format(collectionDate);

        // Start of order segment
        String orderStart = frameNumber + "O" + delimiter;

        // Sample Information
        String sampleInfo = sampleId + caret + "0.0" + caret + "3" + caret + "1" + delimiter;

        // Test Codes
        String testCodes = testNames.stream()
                .map(testName -> caret + caret + caret + testName)
                .collect(Collectors.joining(caret));

        // Order Information
        String orderInfo = specimenCode + delimiter + formattedDate + delimiter
                + delimiter + delimiter // Placeholders for priority and ordering physician
                + testInformation + delimiter + "3" + delimiter; // Additional optional fields

        return orderStart + sampleInfo + testCodes + orderInfo;
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
        // Delimiter and separator setup
        String delimiter = "|";
        String caret = "^";

        // SimpleDateFormat to format the Date object to the required ASTM format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

        // Format the date
        String formattedDate = dateFormat.format(order.getOrderDateTimeStr());

        // Start of order segment
        String orderStart = order.getFrameNumber() + "O" + delimiter;

        // Sample Information
        String sampleInfo = order.getSampleId() + caret + "0.0" + caret + "3" + caret + "1" + delimiter;

        // Test Codes, combining test names into one string separated by carets
        String testCodes = order.getTestNames().stream()
                .map(testName -> caret + caret + caret + testName)
                .collect(Collectors.joining(caret));

        // Order Information
        String orderInfo = order.getSpecimenCode() + delimiter + formattedDate + delimiter
                + delimiter + delimiter // Placeholders for priority and ordering physician
                + order.getTestInformation() + delimiter + "3" + delimiter; // Additional optional fields

        return orderStart + sampleInfo + testCodes + orderInfo;
    }

    public String createLimsTerminationRecord(int frameNumber, char terminationCode) {
        String delimiter = "|";
        String terminationStart = frameNumber + "L" + delimiter;
        String terminationInfo = "1" + delimiter + terminationCode; // '1' is the record number, usually fixed
        return terminationStart + terminationInfo;
    }

    private void processMessage(String data, Socket clientSocket) {
        // Logging the received data for debugging
        logger.debug("Received message to process: " + data);

        // Log ASCII values of each character in the received data
        StringBuilder asciiDebugInfo = new StringBuilder("ASCII sequence of received message: ");
        for (char c : data.toCharArray()) {
            asciiDebugInfo.append("[").append((int) c).append("] "); // Append ASCII value of each character
        }
        logger.debug(asciiDebugInfo.toString());  // Log the complete ASCII sequence of the message

        if (data.contains("H|")) {  // Header Record
            patientDataBundle = new PatientDataBundle();
            receivingQuery = false;
            receivingResults = false;
            respondingQuery = false;
            respondingResults = false;
            needToSendEotForRecordForQuery = false;
            needToSendOrderingRecordForQuery = false;
            needToSendPatientRecordForQuery = false;
            needToSendHeaderRecordForQuery = false;
            logger.debug("Header Record Received: " + data);
        } else if (data.contains("Q|")) {  // Query Record
            receivingQuery = true;
            needToSendHeaderRecordForQuery = true;
            logger.debug("Query Record Received: " + data);
            queryRecord = parseQueryRecord(data);
            patientDataBundle.getQueryRecords().add(queryRecord);
            logger.debug("Parsed the Query Record: " + queryRecord);
        } else if (data.contains("P|")) {  // Patient Record
            logger.debug("Patient Record Received: " + data);
            patientRecord = parsePatientRecord(data);
            patientDataBundle.setPatientRecord(patientRecord);
            logger.debug("Patient Record Parsed: " + patientRecord);
        } else if (data.contains("R|")) {  // Result Record
            logger.debug("Result Record Received: " + data);
            respondingResults = true;
            resultRecord = parseResultsRecord(data);
            patientDataBundle.getResultsRecords().add(resultRecord);
            logger.debug("Result Record Parsed: " + resultRecord);
        } else if (data.contains("L|")) {  // Termination Record
            receivingQuery = false;
            respondingQuery = true;
            logger.debug("Termination Record Received: " + data);
        } else {
            logger.debug("Unknown Record Received: " + data);
        }
    }

//    private String buildMessageWithChecksum(String message, String checkSum) {
//        String formattedMessage = (char) STX + message + (char) ETX + checkSum + (char) CR + (char) LF;
//        StringBuilder asciiDebugInfo = new StringBuilder("Message ASCII Values: ");
//        for (char c : formattedMessage.toCharArray()) {
//            asciiDebugInfo.append("[").append((int) c).append("] ");
//        }
//        logger.debug(asciiDebugInfo.toString());
//        logger.debug("Built message with checksum: " + formattedMessage);
//        return formattedMessage;
//    }
//    private String buildMessageWithChecksum(String message) {
//        int checksum = 0;
//        // Compute checksum based on the content of the message
//        for (char c : message.toCharArray()) {
//            checksum += c;
//        }
//        checksum %= 256; // Ensure the checksum is within one byte
//
//        // Formulate the complete message with STX at the start, ETX after the message, followed by the checksum and CR, LF
//        String checksumHex = String.format("%02X", checksum);
//        String formattedMessage = STX + message + ETX + checksumHex + CR + LF;
//
//        // Logging ASCII values for debugging
//        StringBuilder asciiDebugInfo = new StringBuilder("Message ASCII Values: ");
//        for (char c : formattedMessage.toCharArray()) {
//            asciiDebugInfo.append("[").append((int) c).append("] ");
//        }
//        logger.debug(asciiDebugInfo.toString());
//
//        logger.debug("Built message with checksum: " + formattedMessage);
//        return formattedMessage;
//    }
    // Method to parse the patient segment and return a PatientRecord object
    public static PatientRecord parsePatientRecord(String patientSegment) {
        // Assume the segment format is:
        // <STX>frameNumberP|patientId||additionalId|patientName||patientSecondName|patientSex|||||patientAddress||||patientPhoneNumber|attendingDoctor|<CR><ETX>checksum

        // Split the segment by '|' to extract fields
        String[] fields = patientSegment.split("\\|");

        // Extract frame number and remove non-numeric characters (<STX>, etc.)
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));

        // Extract other fields based on their positions in the segment
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

    // Method to parse the result segment and return a ResultsRecord object
    public static ResultsRecord parseResultsRecord(String resultSegment) {
        // Assume the segment format is:
        // <STX>frameNumberR|testCode|resultValue|resultUnits|resultDateTime|instrumentName<CR><ETX>checksum

        // Split the segment by '|' to extract fields
        String[] fields = resultSegment.split("\\|");

        // Extract frame number and remove non-numeric characters (<STX>, etc.)
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));

        // Extract test code, which might contain multiple caret (^) separated values
        String testCode = fields[1];

        // Handle the result value field to extract the actual numeric value
        String[] resultParts = fields[2].split("\\^");
        double resultValue = 0.0;
        for (String part : resultParts) {
            try {
                resultValue = Double.parseDouble(part);
                break; // Exit loop once a valid number is found
            } catch (NumberFormatException e) {
                // Ignore parts that are not numbers
            }
        }

        // Extract result units
        String resultUnits = fields[3];

        // Extract the result date and time
        String resultDateTime = fields[4];

        // Extract instrument name
        String instrumentName = fields[5];

        // Return a new ResultsRecord object using the extracted data
        return new ResultsRecord(
                frameNumber,
                testCode,
                resultValue,
                resultUnits,
                resultDateTime,
                instrumentName
        );
    }

    public static OrderRecord parseOrderRecord(String orderSegment) {
        // Assume the segment format is:
        // <STX>frameNumberO|sampleId^0.0^3^1|testNames|specimenCode|orderDateTime||||testInformation|3||||||O<CR><ETX>checksum

        // Split the segment by '|' to extract fields
        String[] fields = orderSegment.split("\\|");

        // Extract frame number and remove non-numeric characters (<STX>, etc.)
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));

        // Sample ID and associated data (assuming specific formatting here)
        String[] sampleDetails = fields[1].split("\\^");
        String sampleId = sampleDetails[0];

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

    // Method to parse the query segment and return a QueryRecord object
    public static QueryRecord parseQueryRecord(String querySegment) {
        // Assume the segment format is:
        // <STX>frameNumberQ|sampleId^additionalData1^additionalData2|universalTestId||||||queryType<CR><ETX>checksum

        // Split the segment by '|' to extract fields
        String[] fields = querySegment.split("\\|");

        // Extract frame number and remove non-numeric characters (<STX>, etc.)
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));

        // Sample ID, considering that sample ID and possibly additional data separated by '^'
        String[] sampleDetails = fields[1].split("\\^");
        String sampleId = sampleDetails[0];

        // Universal Test ID - Assuming the test is specified in a caret-separated sequence
        String universalTestId = fields[2].split("\\^")[0];

        // Query Type - Assuming the last field before CR is the query type
        String queryType = fields[fields.length - 1].replaceAll("[^a-zA-Z]", "");  // Removes non-letter characters, e.g., CR, ETX

        // Return a new QueryRecord object using the extracted data
        return new QueryRecord(
                frameNumber,
                sampleId,
                universalTestId,
                queryType
        );
    }

}

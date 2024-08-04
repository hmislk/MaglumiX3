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
    private static final char CR = 0x0D;

    boolean receivingQuery;
    boolean receivingResults;
    boolean respondingQuery;
    boolean respondingResults;
    boolean needToSendHeaderRecordForQuery;
    boolean needToSendPatientRecordForQuery;
    boolean needToSendOrderingRecordForQuery;
    boolean needToSendEotForRecordForQuery;

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
            while (sessionActive) {
                int data = in.read();
                switch (data) {
                    case ENQ:
                        logger.debug("Received ENQ");
                        out.write(ACK);
                        out.flush();
                        logger.debug("Sent ACK");
                        break;
                    case ACK:
                        logger.debug("ACK Received.");
                        if (needToSendHeaderRecordForQuery) {
                            String hm = createLimsHeader(limsName, senderId, processingId, versionNumber);
                            String shm = buildMessageWithChecksum(hm);
                            sendResponse(shm, clientSocket);
                            frameNumber = 2;
                            needToSendHeaderRecordForQuery = false;
                            needToSendPatientRecordForQuery = true;
                            logger.debug("Sent " + shm);
                        } else if (needToSendPatientRecordForQuery) {
                            String pm = createLimsPatientRecord(frameNumber, patientId, patientName, patientSex, referringDocName);
                            String spm = buildMessageWithChecksum(pm);
                            sendResponse(spm, clientSocket);
                            frameNumber = 3;
                            needToSendPatientRecordForQuery = false;
                            needToSendOrderingRecordForQuery = true;
                            logger.debug("Sent " + spm);
                        } else if (needToSendOrderingRecordForQuery) {
                            if (testNames == null || testNames.isEmpty()) {
                                testNames = Arrays.asList("Gcp GP");
                            }
                            String om = createLimsOrderRecord(frameNumber, sampleId, testNames, specimanCode, sampledDate, testInformation);
                            String som = buildMessageWithChecksum(om);
                            sendResponse(som, clientSocket);
                            frameNumber = 4;
                            needToSendOrderingRecordForQuery = false;
                            needToSendEotForRecordForQuery = true;
                            logger.debug("Sent " + som);
                        } else if (needToSendEotForRecordForQuery) {
                            String tmq = createLimsTerminationRecord(frameNumber, terminationCode);
                            String qtmq = buildMessageWithChecksum(tmq);
                            sendResponse(qtmq, clientSocket);
                            needToSendEotForRecordForQuery = false;
                            receivingQuery = false;
                            receivingResults = false;
                            respondingQuery = false;
                            respondingResults = false;
                            logger.debug("Sent " + qtmq);
                        }
                        out.write(EOT);
                        out.flush();
                        logger.debug("Sent EOT");
                        break;
                    case STX:
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
                        logger.debug("EOT Received ");
                        if (respondingQuery) {
                            logger.debug("Starting Transmission to send test requests");
                            out.write(ENQ);
                            out.flush();
                            logger.debug("Sent ENQ");
                            break;
                        } else if (respondingResults) {

                        } else {
                            sessionActive = false;
                            logger.debug("Received EOT, ending session");
                        }
                        break;
                    default:
                        logger.debug("Received unexpected data: " + (char) data);
                        break;
                }
            }
        } catch (IOException e) {
            logger.error("Error during client communication", e);
        }
    }

    public String createLimsHeader(String limsName, String senderId, String processingId, String versionNumber) {
        // Delimiter used in the ASTM protocol
        String delimiter = "|";

        // Start of header segment
        String headerStart = "1H" + delimiter;

        // Encoding characters (fixed as part of the protocol)
        String encodingChars = "\\^&";

        // Combine all parts to form the header
        String header = headerStart
                + delimiter + encodingChars
                + delimiter + delimiter // additional delimiters for unused fields
                + senderId + "^" + limsName + "^" + versionNumber
                + delimiter + delimiter + delimiter // additional delimiters for unused fields
                + processingId;

        return header;
    }

    public String createLimsPatientRecord(int frameNumber, String patientId, String patientName, String patientSex, String attendingDoctor) {
        // Delimiter used in the ASTM protocol
        String delimiter = "|";

        // Start of patient segment
        String patientStart = frameNumber + "P" + delimiter;

        // Patient Information
        // Note: Adding multiple delimiters as placeholders for optional fields not used in the example
        String patientInfo = patientId + delimiter
                + delimiter // Placeholder for optional second ID
                + patientName + delimiter
                + delimiter // Placeholder for patient's second name if applicable
                + delimiter // Placeholder for patient's DOB (Date of Birth)
                + patientSex + delimiter
                + delimiter + delimiter // Placeholders for race and patient's address
                + delimiter + delimiter // Placeholders for reserved fields
                + delimiter + delimiter // Placeholders for phone number and attending physician's ID
                + attendingDoctor + delimiter;

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

    public String createLimsTerminationRecord(int frameNumber, char terminationCode) {
        String delimiter = "|";
        String terminationStart = frameNumber + "L" + delimiter;
        String terminationInfo = "1" + delimiter + terminationCode; // '1' is the record number, usually fixed
        return terminationStart + terminationInfo;
    }

    private void processMessage(String data, Socket clientSocket) {
        if (data.startsWith("1H|")) {  // Header Record
            receivingQuery = false;
            receivingResults = false;
            respondingQuery = false;
            respondingResults = false;
            logger.debug("Header Record Received: " + data);
        } else if (data.startsWith("2Q|")) {  // Query Record
            receivingQuery = true;
            respondingQuery = false;
//            String response = createResponseForTest("Gluc GP");
//            sendResponse(response, clientSocket);
        } else if (data.startsWith("3L|")) {  // Termination Record
            receivingQuery = false;
            respondingQuery = true;
//            String response = createResponseForTest("Gluc GP");
//            sendResponse(response, clientSocket);
        } else if (data.startsWith("2P|")) {  // Patient Record
            logger.debug("Patient Record Received: " + data);
            // Additional logic to handle patient data
        } else if (data.startsWith("3O|") || data.startsWith("4R|")) {  // Order or Result Record
            handleTestResults(data);
            String response = createAcknowledgementResponse();
            sendResponse(response, clientSocket);
        } else if (data.startsWith("5L|")) {  // Terminator Record
            logger.debug("Terminator Record Received: " + data);
        }
    }

    private String createAcknowledgementResponse() {
        String message = "2R|1|||Test Received||";
        return buildMessageWithChecksum(message);
    }

    private String buildMessageWithChecksum(String message) {
        int checksum = 0;
        for (char c : message.toCharArray()) {
            checksum += c;
        }
        checksum %= 256;
        String formattedMessage = String.format("%c%s%c%c%02X", STX, message, ETX, CR, checksum);
        logger.debug("Built message with checksum: " + formattedMessage);
        return formattedMessage;
    }

    private void handleTestResults(String data) {
        logger.info("Test results received: " + data);
        // Additional handling for test results
    }

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

    private void sendResponse(String response, Socket clientSocket) {
        try {
            OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
            out.write(response.getBytes());
            out.write(ACK);
            out.flush();
            logger.debug("Response sent: " + response);
        } catch (IOException e) {
            logger.error("Failed to send response", e);
        }
    }
}

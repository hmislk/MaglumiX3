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
import org.apache.logging.log4j.core.appender.rolling.action.IfAny;

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
    List<String> testNames;
    int frameNumber;
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
            patientRecord = patientDataBundle.getPatientRecord();
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
            orderRecord = patientDataBundle.getOrderRecords().get(0);
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
            patientDataBundle = LISCommunicator.pullOrders(patientDataBundle.getQueryRecords().get(0));
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
            receivingQuery = false;
            respondingQuery = true;
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
            respondingQuery = false;
            resultRecord = parseResultsRecord(data);
            patientDataBundle.getResultsRecords().add(resultRecord);
            logger.debug("Result Record Parsed: " + resultRecord);
        } else if (data.contains("L|")) {  // Termination Record
            logger.debug("Termination Record Received: " + data);
        } else if (data.contains("C|")) {  // COmment Record
            logger.debug("COmment Record Received: " + data);
        } else {
            logger.debug("Unknown Record Received: " + data);
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

        // Extract the frame number by removing non-numeric characters
        int frameNumber = Integer.parseInt(fields[0].replaceAll("[^0-9]", ""));
        logger.debug("Frame number extracted: {}", frameNumber);

        // Test code should be correctly identified assuming it's provided correctly in the fields[1]
        String testCode = fields[1];
        logger.debug("Test code extracted: {}", testCode);

        // Result value parsing assumes the result is in a composite field separated by carets
        double resultValue = 0.0;
        try {
            String[] resultParts = fields[2].split("\\^");
            resultValue = Double.parseDouble(resultParts[3]); // Assuming the result value is always at position 3
            logger.debug("Result value extracted: {}", resultValue);
        } catch (NumberFormatException e) {
            logger.error("Failed to parse result value from segment: {}", resultSegment, e);
        }

        // Units and other details
        String resultUnits = fields[3];
        logger.debug("Result units extracted: {}", resultUnits);
        String resultDateTime = fields[4];
        logger.debug("Result date-time extracted: {}", resultDateTime);
        String instrumentName = fields[5];
        logger.debug("Instrument name extracted: {}", instrumentName);

        // Return a new ResultsRecord object initialized with extracted values
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

    public static String extractSampleId(String astm2Message) {
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

    public static QueryRecord parseQueryRecord(String querySegment) {
        String sampleId = extractSampleId(querySegment);
        System.out.println("Sample ID: " + sampleId); // Debugging
        return new QueryRecord(
                0,
                sampleId,
                "",
                ""
        );
    }

}

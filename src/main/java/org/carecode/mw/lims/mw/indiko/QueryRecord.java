package org.carecode.mw.lims.mw.indiko;

/**
 *
 * @author buddh
 */
public class QueryRecord {

    private int frameNumber;
    private String sampleId;
    private String universalTestId; // Typically 'ALL' or specific test codes
    private String queryType; // O for original, R for repeat, etc., depending on the use case

    // Constructor
    public QueryRecord(int frameNumber, String sampleId, String universalTestId, String queryType) {
        System.out.println("sampleId = " + sampleId);
        this.frameNumber = frameNumber;
        this.sampleId = sampleId;
        this.universalTestId = universalTestId;
        this.queryType = queryType;
    }

    // Getters and Setters
    public int getFrameNumber() {
        return frameNumber;
    }

    public void setFrameNumber(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public String getUniversalTestId() {
        return universalTestId;
    }

    public void setUniversalTestId(String universalTestId) {
        this.universalTestId = universalTestId;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }
}

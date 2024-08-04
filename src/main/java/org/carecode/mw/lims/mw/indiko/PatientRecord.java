package org.carecode.mw.lims.mw.indiko;

public class PatientRecord {

    private int frameNumber;
    private String patientId;
    private String additionalId;
    private String patientName;
    private String patientSecondName; // Unused in your example but included for completeness
    private String patientSex;
    private String race; // Unused in your example but included for completeness
    private String dob; // Date of birth, unused in your example but included for completeness
    private String patientAddress; // Unused in your example but included for completeness
    private String patientPhoneNumber; // Unused in your example but included for completeness
    private String attendingDoctor;

    // Constructor
    public PatientRecord(int frameNumber, String patientId, String additionalId, String patientName, String patientSecondName,
            String patientSex, String race, String dob, String patientAddress, String patientPhoneNumber, String attendingDoctor) {
        this.frameNumber = frameNumber;
        this.patientId = patientId;
        this.additionalId = additionalId;
        this.patientName = patientName;
        this.patientSecondName = patientSecondName;
        this.patientSex = patientSex;
        this.race = race;
        this.dob = dob;
        this.patientAddress = patientAddress;
        this.patientPhoneNumber = patientPhoneNumber;
        this.attendingDoctor = attendingDoctor;
    }

    // Getters and Setters
    public int getFrameNumber() {
        return frameNumber;
    }

    public void setFrameNumber(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getAdditionalId() {
        return additionalId;
    }

    public void setAdditionalId(String additionalId) {
        this.additionalId = additionalId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientSecondName() {
        return patientSecondName;
    }

    public void setPatientSecondName(String patientSecondName) {
        this.patientSecondName = patientSecondName;
    }

    public String getPatientSex() {
        return patientSex;
    }

    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getPatientAddress() {
        return patientAddress;
    }

    public void setPatientAddress(String patientAddress) {
        this.patientAddress = patientAddress;
    }

    public String getPatientPhoneNumber() {
        return patientPhoneNumber;
    }

    public void setPatientPhoneNumber(String patientPhoneNumber) {
        this.patientPhoneNumber = patientPhoneNumber;
    }

    public String getAttendingDoctor() {
        return attendingDoctor;
    }

    public void setAttendingDoctor(String attendingDoctor) {
        this.attendingDoctor = attendingDoctor;
    }
}

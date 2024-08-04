/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.carecode.mw.lims.mw.indiko;

/**
 *
 * @author buddh
 */
import java.util.ArrayList;
import java.util.List;

public class PatientDataBundle {

    private PatientRecord patientRecord;
    private List<ResultsRecord> resultsRecords;
    private List<OrderRecord> orderRecords;
    private List<QueryRecord> queryRecords;

    // Constructor
    public PatientDataBundle() {
        this.resultsRecords = new ArrayList<>();
        this.orderRecords = new ArrayList<>();
        this.queryRecords = new ArrayList<>();
    }

    // Set and retrieve the single PatientRecord
    public void setPatientRecord(PatientRecord record) {
        this.patientRecord = record;
    }

    public PatientRecord getPatientRecord() {
        return patientRecord;
    }

    // Add and retrieve methods for ResultsRecord
    public void addResultsRecord(ResultsRecord record) {
        this.resultsRecords.add(record);
    }

    public List<ResultsRecord> getResultsRecords() {
        return resultsRecords;
    }

    // Add and retrieve methods for OrderRecord
    public void addOrderRecord(OrderRecord record) {
        this.orderRecords.add(record);
    }

    public List<OrderRecord> getOrderRecords() {
        return orderRecords;
    }

    // Add and retrieve methods for QueryRecord
    public void addQueryRecord(QueryRecord record) {
        this.queryRecords.add(record);
    }

    public List<QueryRecord> getQueryRecords() {
        return queryRecords;
    }
}

package org.endeavourhealth.core.database.dal.publisherStaging.models;

import com.google.common.base.Strings;
import org.endeavourhealth.core.database.dal.publisherTransform.models.ResourceFieldMappingAudit;
import org.endeavourhealth.core.database.rdbms.publisherStaging.models.RdbmsStagingSURCC;

import java.util.Date;

public class StagingSURCC {
    private String exchangeId;
    private Date dtReceived;
    private int recordChecksum;
    private int surgicalCaseId;
    private Date dtExtract;
    private boolean activeInd;
    private int personId;
    private int encounterId;
    private Date dtCancelled;
    private String institutionCode;
    private String departmentCode;
    private String surgicalAreaCode;
    private String theatreNumberCode;
    private ResourceFieldMappingAudit audit = null;

    public StagingSURCC() {}

    public StagingSURCC(RdbmsStagingSURCC proxy) throws Exception {
        this.exchangeId = proxy.getExchangeId();
        this.dtReceived = proxy.getDTReceived();
        this.recordChecksum = proxy.getRecordChecksum();
        this.surgicalCaseId = proxy.getSurgicalCaseId();
        this.dtExtract = proxy.getDTExtract();
        this.activeInd = proxy.getActiveInd();
        this.personId = proxy.getPersonId();
        this.encounterId = proxy.getEncounterId();
        this.dtCancelled = proxy.getDTCancelled();
        this.institutionCode = proxy.getInstituteCode();
        this.departmentCode = proxy.getDepartmentCode();
        this.surgicalAreaCode = proxy.getSurgicalAreaCode();
        this.theatreNumberCode = proxy.getTheatreNumberCode();

        if (!Strings.isNullOrEmpty(proxy.getAuditJson())) {
            this.audit = ResourceFieldMappingAudit.readFromJson(proxy.getAuditJson());
        }
    }

//    public StagingCds(long rowId,
//                      long multiLexProductId,
//                      String ctv3ReadCode,
//                      String ctv3ReadTerm,
//                      ResourceFieldMappingAudit audit) {
//        this.rowId = rowId;
//        this.multiLexProductId = multiLexProductId;
//        this.ctv3ReadCode = ctv3ReadCode;
//        this.ctv3ReadTerm = ctv3ReadTerm;
//        this.audit = audit;
//    }

    public String getExchangeId() {
        return exchangeId;
    }
    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public Date getDTReceived() {
        return dtReceived;
    }
    public void setDTReceived(Date dtReceived) {
        this.dtReceived = dtReceived;
    }

    public int getRecordChecksum() {
        return recordChecksum;
    }
    public void setRecordChecksum(int recordChecksum) {
        this.recordChecksum = recordChecksum;
    }

    public int getSurgicalCaseId() {
        return surgicalCaseId;
    }
    public void setSurgicalCaseId(int surgicalCaseId) {
        this.surgicalCaseId = surgicalCaseId;
    }

    public Date getDTExtract() {
        return dtExtract;
    }
    public void setDTExtract(Date dtExtract) { this.dtExtract = dtExtract; }

    public boolean getActiveInd() {
        return activeInd;
    }
    public void setActiveInd(boolean activeInd) {
        this.activeInd = activeInd;
    }

    public int getPersonId  () {
        return personId ;
    }
    public void setPersonId (int personId ) {
        this.personId = personId;
    }

    public int getEncounterId () {
        return encounterId ;
    }
    public void setEncounterId (int encounterId ) {
        this.encounterId = encounterId;
    }

    public Date getDTCancelled () {
        return dtCancelled;
    }
    public void setDTCancelled (Date dtCancelled ) {
        this.dtCancelled = dtCancelled;
    }

    public String getInstituteCode () {
        return institutionCode;
    }
    public void setInstitutionCode (String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public String getDepartmentCode () {
        return departmentCode;
    }
    public void setDepartmentCode (String departmentCode ) { this.departmentCode = departmentCode; }

    public String getSurgicalAreaCode () {
        return surgicalAreaCode;
    }
    public void setSurgicalAreaCode (String surgicalAreaCode) { this.surgicalAreaCode = surgicalAreaCode; }

    public String getTheatreNumberCode () {
        return theatreNumberCode;
    }
    public void setTheatreNumberCode (String theatreNumberCode) {
        this.theatreNumberCode = theatreNumberCode;
    }

    public ResourceFieldMappingAudit getAudit() {
        return audit;
    }
    public void setAudit(ResourceFieldMappingAudit audit) {
        this.audit = audit;
    }
}
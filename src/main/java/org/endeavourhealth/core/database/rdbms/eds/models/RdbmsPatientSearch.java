package org.endeavourhealth.core.database.rdbms.eds.models;

import org.endeavourhealth.core.database.dal.eds.models.PatientSearch;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "patient_search")
public class RdbmsPatientSearch implements Serializable {

    private String serviceId = null;
    private String nhsNumber = null;
    private String forenames = null;
    private String surname = null;
    private Date dateOfBirth = null;
    private Date dateOfDeath = null;
    private String postcode = null;
    private String gender = null;
    private Date registrationStart = null;
    private Date registrationEnd = null;
    private String patientId = null;
    private Date lastUpdated = null;
    private String organisationTypeCode = null;
    private String registrationTypeCode = null;

    public RdbmsPatientSearch() {}

    public RdbmsPatientSearch(PatientSearch proxy) {
        this.serviceId = proxy.getServiceId().toString();
        this.nhsNumber = proxy.getNhsNumber();
        this.forenames = proxy.getForenames();
        this.surname = proxy.getSurname();
        this.dateOfBirth = proxy.getDateOfBirth();
        this.dateOfDeath = proxy.getDateOfDeath();
        this.postcode = proxy.getPostcode();
        this.gender = proxy.getGender();
        this.registrationStart = proxy.getRegistrationStart();
        this.registrationEnd = proxy.getRegistrationEnd();
        this.patientId = proxy.getPatientId().toString();
        this.organisationTypeCode = proxy.getOrganisationTypeCode();
        this.registrationTypeCode = proxy.getRegistrationTypeCode();
    }

    @Id
    @Column(name = "service_id", nullable = false)
    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Column(name = "nhs_number")
    public String getNhsNumber() {
        return nhsNumber;
    }

    public void setNhsNumber(String nhsNumber) {
        this.nhsNumber = nhsNumber;
    }

    @Column(name = "forenames")
    public String getForenames() {
        return forenames;
    }

    public void setForenames(String forenames) {
        this.forenames = forenames;
    }

    @Column(name = "surname")
    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    @Column(name = "date_of_birth")
    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    @Column(name = "date_of_death")
    public Date getDateOfDeath() {
        return dateOfDeath;
    }

    public void setDateOfDeath(Date dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
    }

    @Column(name = "postcode")
    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    @Column(name = "gender")
    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    @Column(name = "registration_start")
    public Date getRegistrationStart() {
        return registrationStart;
    }

    public void setRegistrationStart(Date registrationStart) {
        this.registrationStart = registrationStart;
    }

    @Column(name = "registration_end")
    public Date getRegistrationEnd() {
        return registrationEnd;
    }

    public void setRegistrationEnd(Date registrationEnd) {
        this.registrationEnd = registrationEnd;
    }

    @Id
    @Column(name = "patient_id", nullable = false)
    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    @Column(name = "last_updated", nullable = false)
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Column(name = "organisation_type_code", nullable = true)
    public String getOrganisationTypeCode() {
        return organisationTypeCode;
    }

    public void setOrganisationTypeCode(String organisationTypeCode) {
        this.organisationTypeCode = organisationTypeCode;
    }

    @Column(name = "registration_type_code", nullable = true)
    public String getRegistrationTypeCode() {
        return registrationTypeCode;
    }

    public void setRegistrationTypeCode(String registrationTypeCode) {
        this.registrationTypeCode = registrationTypeCode;
    }
}

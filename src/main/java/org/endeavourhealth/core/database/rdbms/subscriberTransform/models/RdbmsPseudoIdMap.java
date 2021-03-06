package org.endeavourhealth.core.database.rdbms.subscriberTransform.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "pseudo_id_map")
public class RdbmsPseudoIdMap implements Serializable {

    private String patientId = null;
    //private String enterpriseConfigName = null;
    private String pseudoId = null;

    public RdbmsPseudoIdMap() {}

    @Id
    @Column(name = "patient_id", nullable = false)
    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    /*@Id
    @Column(name = "enterprise_config_name", nullable = false)
    public String getEnterpriseConfigName() {
        return enterpriseConfigName;
    }

    public void setEnterpriseConfigName(String enterpriseConfigName) {
        this.enterpriseConfigName = enterpriseConfigName;
    }*/

    @Column(name = "pseudo_id", nullable = false)
    public String getPseudoId() {
        return pseudoId;
    }

    public void setPseudoId(String pseudoId) {
        this.pseudoId = pseudoId;
    }
}

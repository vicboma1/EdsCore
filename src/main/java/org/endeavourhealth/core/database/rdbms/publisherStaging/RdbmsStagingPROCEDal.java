package org.endeavourhealth.core.database.rdbms.publisherStaging;

import org.endeavourhealth.core.database.dal.publisherStaging.StagingPROCEDalI;
import org.endeavourhealth.core.database.dal.publisherStaging.models.StagingPROCE;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.endeavourhealth.core.database.rdbms.publisherStaging.models.RdbmsStagingPROCE;
import org.hibernate.internal.SessionImpl;
import org.hl7.fhir.instance.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

public class RdbmsStagingPROCEDal implements StagingPROCEDalI {
    private static final Logger LOG = LoggerFactory.getLogger(RdbmsStagingPROCEDal.class);



    @Override
    public List<UUID> getSusResourceMappings(UUID serviceId, String sourceRowId, Enumerations.ResourceType resourceType) throws Exception {
        return null;
    }

    @Override
    public void save(StagingPROCE stagingPROCE, UUID serviceId) throws Exception {

        if (stagingPROCE == null) {
            throw new IllegalArgumentException("mapping is null");
        }

        RdbmsStagingPROCE dbObj = new RdbmsStagingPROCE(stagingPROCE);

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;

        try {
            entityManager.getTransaction().begin();

            //have to use prepared statement as JPA doesn't support upserts
            //entityManager.persist(emisMapping);

            SessionImpl session = (SessionImpl) entityManager.getDelegate();
            Connection connection = session.connection();


            String sql = "INSERT INTO staging_PROCE "
                    + " (exchange_id, dt_received, record_checksum, procedure_id, "
                    + " active_ind, encounter_id, procedure_dt_tm, procedure_type, "
                    + " procedure_code, procedure_term, procedure_seq_nbr, lookup_person_id, "
                    + " lookup_mrn, lookup_nhs_number, lookup_date_of_birth)  "
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    + " ON DUPLICATE KEY UPDATE "
                    + " exchange_id = VALUES(exchange_id), "
                    + " dt_received = VALUES(dt_received), "
                    + " record_checksum = VALUES(dt_received), "
                    + " procedure_id = VALUES(procedure_id), "
                    + " active_ind = VALUES(active_ind), "
                    + " encounter_id = VALUES(encounter_id), "
                    + " procedure_dt_tm = VALUES(procedure_id), "
                    + " procedure_type = VALUES(procedure_type), "
                    + " procedure_code = VALUES(procedure_code), "
                    + " procedure_term = VALUES(procedure_term), "
                    + " procedure_seq_nbr = VALUES(procedure_seq_nbr), "
                    + " lookup_person_id = VALUES(lookup_person_id), "
                    + " lookup_mrn = VALUES(lookup_mrn), "
                    + " lookup_nhs_number = VALUES(lookup_nhs_number), "
                    + " lookup_date_of_birth = VALUES(lookup_date_of_birth) ";
            ps = connection.prepareStatement(sql);

            ps.setString(1, dbObj.getExchangeId());
            java.sql.Date sqlDate = new java.sql.Date(dbObj.getDateReceived().getTime());
            ps.setDate(2,sqlDate);
            ps.setInt(3,dbObj.getCheckSum());
            ps.setInt(4,dbObj.getProcedureId());
            ps.setBoolean(5,dbObj.isActiveInd());
            ps.setInt(6,dbObj.getEncounterId());
            sqlDate = new java.sql.Date(dbObj.getProcedureDtTm().getTime());
            ps.setDate(7,sqlDate);
            ps.setString(8,dbObj.getProcedureType());
            ps.setString(9,dbObj.getProcedureCode());
            ps.setString(10,dbObj.getProcedureTerm());
            ps.setInt(12,dbObj.getProcedureSeqNo());
            ps.setInt(12,dbObj.getLookupPersonId());
            ps.setString(13,dbObj.getLookupMrn());
            ps.setString(14,dbObj.getLookupNhsNumber());
            sqlDate = new java.sql.Date(dbObj.getLookupDateOfBirth().getTime());
            ps.setDate(15,sqlDate);



//            if (dbObj.getAuditJson() == null) {
//                ps.setNull(11, Types.VARCHAR);
//            } else {
//                ps.setString(11, dbObj.getAuditJson());
//            }

            ps.executeUpdate();

            //transaction.commit();
            entityManager.getTransaction().commit();

        } catch (Exception ex) {
            entityManager.getTransaction().rollback();
            throw ex;

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }

}
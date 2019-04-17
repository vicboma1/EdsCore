package org.endeavourhealth.core.database.rdbms.publisherStaging;

import org.endeavourhealth.core.database.dal.publisherStaging.StagingProcedureDalI;
import org.endeavourhealth.core.database.dal.publisherStaging.models.StagingProcedure;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.endeavourhealth.core.database.rdbms.publisherStaging.models.RdbmsStagingProcedure;
import org.hibernate.internal.SessionImpl;
import org.hl7.fhir.instance.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

public class RdbmsStagingProcedureDal implements StagingProcedureDalI {
    private static final Logger LOG = LoggerFactory.getLogger(RdbmsStagingProcedureDal.class);



    @Override
    public List<UUID> getSusResourceMappings(UUID serviceId, String sourceRowId, Enumerations.ResourceType resourceType) throws Exception {
        return null;
    }

    @Override
    public void saveStagingProcedure(StagingProcedure stagingProcedure) throws Exception {

        if (stagingProcedure == null) {
            throw new IllegalArgumentException("mapping is null");
        }

        RdbmsStagingProcedure dbObj = new RdbmsStagingProcedure(stagingProcedure);
        UUID serviceId = stagingProcedure.getServiceId();

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;

        try {
            entityManager.getTransaction().begin();

            //have to use prepared statement as JPA doesn't support upserts
            //entityManager.persist(emisMapping);

            SessionImpl session = (SessionImpl) entityManager.getDelegate();
            Connection connection = session.connection();

            //primary key (service_id, nomenclature_id)
            String sql1 = "INSERT INTO staging_procedure "
                    + "(exchange_id,encntr_id, person_id, consultant, "
                    + "proc_dt_tm, updt_by,create_dt_tm, proc_cd_type, proc_cd)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " exchange_id = VALUES(exchange_id), "
                    + " person_id = VALUES(person_id),"
                    + " consultant = VALUES(consultant),"
                    + " proc_dt_tm = VALUES(proc_dt_tm),"
                    + " updt_by = VALUES (updt_by),"
                    + " create_dt_tm = VALUES(create_dt_tm),"
                    + " proc_cd_type = VALUES(proc_cd_type),"
                    + " proc_cd = VALUES(proc_cd)";

            int col = 1;

            String sql = "INSERT INTO staging_procedure "
                    + " (exchange_id, dt_received, record_checksum, mrn, "
                    + " nhs_number, date_of_birth, encounter_id, consultant, "
                    + " proc_dt_tm, updated_by, freetext_comment, create_dt_tm, "
                    + " proc_cd_type, proc_cd, proc_term, person_id, ward, site, "
                    + " lookup_person_id, lookup_consultant_personnel_id, "
                    + " lookup_recorded_by_personnel_id)"
                    + " VALUES()"
                    + " ON DUPLICATE KEY UPDATE "
                    + " exchange_id = VALUES(exchange_id), "
                    + " dt_received = VALUES(dt_received), "
                    + " record_checksum = VALUES(dt_received), "
                    + " mrn = VALUES(mrn), "
                    + " nhs_number = VALUES(nhs_number), "
                    + " date_of_birth = VALUES(date_of_birth), "
                    + " encounter_id = VALUES(encounter_id), "
                    + " consultant = VALUES(consultant), "
                    + " proc_dt_tm = VALUES(proc_dt_tm), "
                    + " updated_by = VALUES(updated_by), "
                    + " freetext_comment = VALUES(freetext_comment), "
                    + " create_dt_tm = VALUES(create_dt_tm), "
                    + " proc_cd_type = VALUES(proc_cd_type), "
                    + " proc_cd = VALUES(proc_cd), "
                    + " proc_term = VALUES(proc_term), "
                    + " person_id = VALUES(person_id), "
                    + " ward = VALUES(ward), "
                    + " site = VALUES(site), "
                    + " lookup_person_id = VALUES(lookup_person), "
                    + " lookup_consultant_personnel_id = VALUES(lookup_consultant_personnel_id), "
                    + " lookup_recorded_by_personnel_id = VALUES(lookup_recorded_by_personnel_id)";


            ps = connection.prepareStatement(sql);

            ps.setString(1, dbObj.getExchangeId());
            java.sql.Date sqlDate = new java.sql.Date(dbObj.getDateReceived().getTime());
            ps.setDate(2,sqlDate);
            ps.setInt(3,dbObj.getCheckSum());
            ps.setString(4,dbObj.getMrn());
            ps.setString(5,dbObj.getNhsNumber());
            sqlDate = new java.sql.Date(dbObj.getDob().getTime());
            ps.setDate(6,sqlDate);
            ps.setInt(7,dbObj.getEncounterId());
            ps.setString(8,dbObj.getConsultant());
            sqlDate = new java.sql.Date(dbObj.getProc_dt_tm().getTime());
            ps.setDate(9,sqlDate);
            ps.setInt(10,dbObj.getUpdatedBy());
            ps.setString(11,dbObj.getComments());
            sqlDate = new java.sql.Date(dbObj.getCreate_dt_tm().getTime());
            ps.setDate(12,sqlDate);
            ps.setString(13,dbObj.getProcedureCodeType());
            ps.setString(14,dbObj.getProcedureCode());
            ps.setString(15,dbObj.getProcedureTerm());
            ps.setInt(16,dbObj.getPersonId());
            ps.setString(17,dbObj.getWard());
            ps.setString(18,dbObj.getSite());
            ps.setInt(19,dbObj.getLookupPersonId());
            ps.setInt(20,dbObj.getLookupConsultantPersonnelId());
            ps.setInt(21,dbObj.getLookuprecordedByPersonnelId());


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
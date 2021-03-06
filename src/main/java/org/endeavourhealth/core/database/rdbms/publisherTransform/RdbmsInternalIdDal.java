package org.endeavourhealth.core.database.rdbms.publisherTransform;

import org.endeavourhealth.core.database.dal.publisherTransform.InternalIdDalI;
import org.endeavourhealth.core.database.dal.publisherTransform.models.InternalIdMap;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.endeavourhealth.core.database.rdbms.DeadlockHandler;
import org.endeavourhealth.core.database.rdbms.publisherTransform.models.RdbmsInternalIdMap;
import org.hibernate.internal.SessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class RdbmsInternalIdDal implements InternalIdDalI {

    private static final Logger LOG = LoggerFactory.getLogger(RdbmsInternalIdDal.class);

    @Override
    public String getDestinationId(UUID serviceId, String idType, String sourceId) throws Exception {
        //LOG.trace("readMergeRecordDB:" + idType + " " + sourceId);
        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {
            String sql = "select c"
                    + " from"
                    + " RdbmsInternalIdMap c"
                    + " where c.serviceId = :service_id"
                    + " and c.idType = :id_type"
                    + " and c.sourceId = :source_id";

            Query query = entityManager.createQuery(sql, RdbmsInternalIdMap.class)
                    .setParameter("service_id", serviceId.toString())
                    .setParameter("id_type", idType)
                    .setParameter("source_id", sourceId)
                    .setMaxResults(1);

            try {
                return ((RdbmsInternalIdMap) query.getSingleResult()).getDestinationId();
            }
            catch (NoResultException e) {
                return null;
            }
        } finally {
            if (entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }

    @Override
    public Map<String, String> getDestinationIds(UUID serviceId, String idType, Set<String> sourceIds) throws Exception {

        if (sourceIds.isEmpty()) {
            return new HashMap<>();
        }

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);

        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            String sql = "SELECT source_id, destination_id"
                    + " FROM internal_id_map"
                    + " WHERE service_id = ?"
                    + " AND id_type = ?"
                    + " AND source_id IN (";
            for (int i=0; i<sourceIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ")";

            ps = connection.prepareStatement(sql);

            int col = 1;
            ps.setString(col++, serviceId.toString());
            ps.setString(col++, idType);
            for (String sourceId: sourceIds) {
                ps.setString(col++, sourceId);
            }

            Map<String, String> ret = new HashMap<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;
                String sourceId = rs.getString(col++);
                String destId = rs.getString(col++);

                ret.put(sourceId, destId);
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }

    @Override
    public List<InternalIdMap> getSourceId(UUID serviceId, String idType, String destinationId) throws Exception {
        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {
            String sql = "select c"
                    + " from"
                    + " RdbmsInternalIdMap c"
                    + " where c.serviceId = :service_id"
                    + " and c.idType = :id_type"
                    + " and c.destinationId = :destination_id";

            Query query = entityManager.createQuery(sql, RdbmsInternalIdMap.class)
                    .setParameter("service_id", serviceId.toString())
                    .setParameter("id_type", idType)
                    .setParameter("destination_id", destinationId);

            List<RdbmsInternalIdMap> results = query.getResultList();
            return results
                    .stream()
                    .map(T -> new InternalIdMap((T)))
                    .collect(Collectors.toList());

        } finally {
            if (entityManager.isOpen()) {
                entityManager.close();
            }
        }
    }

    @Override
    public void save(UUID serviceId, String idType, String sourceId, String destinationId) throws Exception {
        //LOG.trace("insertMergeRecord:" + idType + " " + sourceId + " " + destinationId);

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);

        PreparedStatement ps = null;
        try {
            entityManager.getTransaction().begin();

            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            String sql = "INSERT INTO internal_id_map"
                    + " (service_id, id_type, source_id, destination_id, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " destination_id = VALUES(destination_id),"
                    + " updated_at = VALUES(updated_at)";

            ps = connection.prepareStatement(sql);

            int col = 1;
            ps.setString(col++, serviceId.toString());
            ps.setString(col++, idType);
            ps.setString(col++, sourceId);
            ps.setString(col++, destinationId);
            ps.setTimestamp(col++, new java.sql.Timestamp(new Date().getTime()));

            ps.executeUpdate();

            entityManager.getTransaction().commit();
            //LOG.trace("Saved mergeRecord for " + resourceType + " " + resourceFrom.toString() + "==>" + resourceTo.toString());

        } catch (Exception ex) {
            LOG.error("Exception executing prepared statement " + ps);
            entityManager.getTransaction().rollback();
            throw ex;

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }

    @Override
    public void save(List<InternalIdMap> mappingsParam) throws Exception {
        //allow several attempts if it fails due to a deadlock
        List<InternalIdMap> mappings = Collections.synchronizedList(mappingsParam);
        DeadlockHandler h = new DeadlockHandler();
        while (true) {
            try {
                trySave(mappings);
                break;

            } catch (Exception ex) {
                h.handleError(ex);
            }
        }

    }

    public void trySave(List<InternalIdMap> mappingsParam) throws Exception {

        List<InternalIdMap> mappings = new ArrayList<>(mappingsParam);
        UUID serviceId = findServiceId(mappings);

        //remove any duplicates - because we generally populate mappings from multiple threads, if files contain
        //duplicated data, we end up trying to save the same mapping more than once, which results in deadlocks
        Set<String> hsUniques = new HashSet<>();
        for (int i=mappings.size()-1; i>=0; i--) {
            InternalIdMap mapping = mappings.get(i);
            String key = mapping.getIdType() + "|" + mapping.getSourceId();
            if (!hsUniques.contains(key)) {
                hsUniques.add(key);
            } else {
                mappings.remove(i);
            }
        }

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);

        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            String sql = "INSERT INTO internal_id_map"
                    + " (service_id, id_type, source_id, destination_id, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " destination_id = VALUES(destination_id),"
                    + " updated_at = VALUES(updated_at)";

            ps = connection.prepareStatement(sql);

            entityManager.getTransaction().begin();

            for (InternalIdMap mapping: mappings) {

                int col = 1;
                ps.setString(col++, mapping.getServiceId().toString());
                ps.setString(col++, mapping.getIdType());
                ps.setString(col++, mapping.getSourceId());
                ps.setString(col++, mapping.getDestinationId());
                ps.setTimestamp(col++, new java.sql.Timestamp(new Date().getTime()));

                ps.addBatch();
            }

            ps.executeBatch();

            entityManager.getTransaction().commit();
            //LOG.trace("Saved mergeRecord for " + resourceType + " " + resourceFrom.toString() + "==>" + resourceTo.toString());

        } catch (Exception ex) {
            LOG.error("Exception executing prepared statement " + ps);
            for (InternalIdMap mapping: mappings) {
                LOG.error(mapping.getServiceId().toString() + " " + mapping.getIdType() + " " + mapping.getSourceId() + " -> " + mapping.getDestinationId());
            }
            entityManager.getTransaction().rollback();
            throw ex;

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }

    private UUID findServiceId(List<InternalIdMap> mappings) throws Exception {

        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("trying to save null or empty mappings");
        }

        UUID serviceId = null;
        for (InternalIdMap mapping: mappings) {
            if (serviceId == null) {
                serviceId = mapping.getServiceId();
            } else if (!serviceId.equals(mapping.getServiceId())) {
                throw new IllegalArgumentException("Can't save resources for different services");
            }
        }
        return serviceId;
    }
}

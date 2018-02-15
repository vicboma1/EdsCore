package org.endeavourhealth.core.database.rdbms.publisherTransform;

import org.endeavourhealth.core.database.dal.ehr.models.ResourceWrapper;
import org.endeavourhealth.core.database.dal.publisherTransform.SourceFileMappingDalI;
import org.endeavourhealth.core.database.dal.publisherTransform.models.ResourceFieldMapping;
import org.endeavourhealth.core.database.dal.publisherTransform.models.ResourceFieldMappingAudit;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.endeavourhealth.core.database.rdbms.publisherTransform.models.*;
import org.hibernate.internal.SessionImpl;
import org.hl7.fhir.instance.model.ResourceType;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class RdbmsSourceFileMappingDal implements SourceFileMappingDalI {

    private static final String CSV_DELIM = "|";

    /**
     * checks for a pre-existing audit of a CSV/tab-delimited file
     */
    @Override
    public Integer findFileAudit(UUID serviceId, UUID systemId, UUID exchangeId, int fileTypeId, String filePath) throws Exception {

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            String sql = "SELECT id"
                    + " FROM source_file"
                    + " WHERE service_id = ?"
                    + " AND system_id = ?"
                    + " AND source_file_type_id = ?"
                    + " AND exchange_id = ?"
                    + " AND file_path = ?;";

            ps = connection.prepareStatement(sql);

            ps.setString(1, serviceId.toString());
            ps.setString(2, systemId.toString());
            ps.setInt(3, fileTypeId);
            ps.setString(4, exchangeId.toString());
            ps.setString(5, filePath.toString());

            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                return new Integer(resultSet.getInt(1));
            } else {
                return null;
            }

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }


    /**
     * audits a received CSV/tab-delimited file and returns the ID of that audit record
     */
    public int auditFile(UUID serviceId, UUID systemId, UUID exchangeId, int fileTypeId, String filePath) throws Exception {

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {
            RdbmsSourceFile fileObj = new RdbmsSourceFile();
            fileObj.setServiceId(serviceId.toString());
            fileObj.setSystemId(systemId.toString());
            fileObj.setExchangeId(exchangeId.toString());
            fileObj.setFilePath(filePath);
            fileObj.setInsertedAt(new Date());
            fileObj.setSourceFileTypeId(fileTypeId);

            entityManager.getTransaction().begin();
            entityManager.persist(fileObj);
            entityManager.getTransaction().commit();

            return fileObj.getId().intValue();

        } finally {
            entityManager.close();
        }
    }

    /**
     * retrieves the IDs of all field audits for the given file, in a corresponding grid (2D array)
     */
    @Override
    public Long findRecordAuditIdForRow(UUID serviceId, int fileAuditId, int rowIndex) throws Exception {

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            String sql = "SELECT id"
                    + " FROM source_file_record"
                    + " WHERE source_file_id = ?"
                    + " AND source_location = ?"
                    + " LIMIT 1";

            ps = connection.prepareStatement(sql);

            ps.setInt(1, fileAuditId);
            ps.setString(2, "" + rowIndex);

            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                return new Long(resultSet.getLong(1));
            } else {
                return null;
            }

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }
    }

    @Override
    public int findOrCreateFileTypeId(UUID serviceId, String typeDescription, List<String> columns) throws Exception {
        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;
        try {

            Map<Integer, List<String>> hmMatches = new HashMap<>();

            //first we need to find the ID of the matching file type (or create if we can't match)
            String sql = "SELECT t.id, c.column_name"
                    + " FROM source_file_type t"
                    + " INNER JOIN source_file_type_column c"
                    + " ON t.id = c.source_file_type_id"
                    + " WHERE t.description = ?"
                    + " ORDER BY t.id, c.column_index;";

            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            ps = connection.prepareStatement(sql);

            ps.setString(1, typeDescription);

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                Integer id = new Integer(resultSet.getInt(1));
                String column = resultSet.getString(2);

                List<String> l = hmMatches.get(id);
                if (l == null) {
                    l = new ArrayList<>();
                    hmMatches.put(id, l);
                }
                l.add(column);
            }


            //go through the existing file types to look for a match on the column names
            for (Integer id: hmMatches.keySet()) {
                List<String> foundColumns = hmMatches.get(id);

                if (foundColumns.size() != columns.size()) {
                    continue;
                }

                boolean matches = true;

                for (int i=0; i<foundColumns.size(); i++) {
                    String foundColumn = foundColumns.get(i);
                    String column = columns.get(i);
                    if (!foundColumn.equalsIgnoreCase(column)) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    return id.intValue();
                }
            }

            //if we didn't find a match, then create a new one
            entityManager.getTransaction().begin();

            RdbmsSourceFileType fileTypeObj = new RdbmsSourceFileType();
            fileTypeObj.setDescription(typeDescription);
            entityManager.persist(fileTypeObj);

            int newId = fileTypeObj.getId().intValue();

            for (int i=0; i<columns.size(); i++) {
                String column = columns.get(i);

                RdbmsSourceFileTypeColumn columnObj = new RdbmsSourceFileTypeColumn();
                columnObj.setSourceFileTypeId(newId);
                columnObj.setColumnIndex(i);
                columnObj.setColumnName(column);

                entityManager.persist(columnObj);
            }

            entityManager.getTransaction().commit();

            return newId;

        } finally {
            entityManager.close();
            if (ps != null) {
                ps.close();
            }
        }
    }

    public long auditFileRow(UUID serviceId, String[] values, int recordLineNumber, int sourceFileId) throws Exception {


        String rowStr = String.join(CSV_DELIM, values);

        long recordAuditId;

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {
            entityManager.getTransaction().begin();

            RdbmsSourceFileRecord fieldObj = new RdbmsSourceFileRecord();
            fieldObj.setSourceFileId(sourceFileId);

            fieldObj.setSourceFileId(sourceFileId);
            fieldObj.setSourceLocation("" + recordLineNumber);
            fieldObj.setValue(rowStr);
            entityManager.persist(fieldObj);

            recordAuditId = fieldObj.getId().longValue();

            entityManager.getTransaction().commit();

        } finally {
            entityManager.close();
        }

        return recordAuditId;
    }


    public List<ResourceFieldMapping> findFieldMappings(UUID serviceId, ResourceType resourceType, UUID resourceId) throws Exception {

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {

            //retrieve the resource mappings, which contain JSON telling us which rows and cols to look up
            List<RdbmsResourceFieldMappings> resourceMappings = findResourceMappings(entityManager, resourceType, resourceId);

            //for each resource mapping, retrieve the raw file records
            List<Long> distinctRowIds = findDistinctAuditRowIds(resourceMappings, null);
            List<RdbmsSourceFileRecord> fileRecords = findSourceFileRecords(entityManager,  distinctRowIds);

            //for each raw file record, retrieve the main file metadata
            List<Integer> distinctFileIds = findDistinctFileIds(fileRecords);
            List<RdbmsSourceFile> sourceFiles = findSourceFiles(entityManager, distinctFileIds);

            return createFileMappingObjects(resourceMappings, fileRecords, sourceFiles, null);

        } finally {
            entityManager.close();
        }
    }

    public List<ResourceFieldMapping> findFieldMappingsForField(UUID serviceId, ResourceType resourceType, UUID resourceId, String field) throws Exception {

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {

            //retrieve the resource mappings, which contain JSON telling us which rows and cols to look up
            List<RdbmsResourceFieldMappings> resourceMappings = findResourceMappings(entityManager, resourceType, resourceId);

            //for each resource mapping, retrieve the raw file records
            List<Long> distinctRowIds = findDistinctAuditRowIds(resourceMappings, field);
            List<RdbmsSourceFileRecord> fileRecords = findSourceFileRecords(entityManager, distinctRowIds);

            //for each raw file record, retrieve the main file metadata
            List<Integer> distinctFileIds = findDistinctFileIds(fileRecords);
            List<RdbmsSourceFile> sourceFiles = findSourceFiles(entityManager, distinctFileIds);

            return createFileMappingObjects(resourceMappings, fileRecords, sourceFiles, field);

        } finally {
            entityManager.close();
        }
    }

    private List<ResourceFieldMapping> createFileMappingObjects(List<RdbmsResourceFieldMappings> resourceMappings,
                                                                List<RdbmsSourceFileRecord> fileRecords,
                                                                List<RdbmsSourceFile> sourceFiles,
                                                                String specificField) throws Exception {

        List<ResourceFieldMapping> ret = new ArrayList<>();

        //hash two of the lists by their ID for speed of lookup
        Map<Long, RdbmsSourceFileRecord> hmFileRecords = new HashMap<>();
        for (RdbmsSourceFileRecord fileRecord: fileRecords) {
            hmFileRecords.put(fileRecord.getId(), fileRecord);
        }

        Map<Integer, RdbmsSourceFile> hmSourceFiles = new HashMap<>();
        for (RdbmsSourceFile sourceFile: sourceFiles) {
            hmSourceFiles.put(sourceFile.getId(), sourceFile);
        }

        Map<String, String> hmFieldVersions = new HashMap<>();
        //Set<String> fieldsDoneSet = new HashSet<>();

        for (int i=resourceMappings.size()-1; i>=0; i--) {
            RdbmsResourceFieldMappings mapping = resourceMappings.get(i);
            String mappingJson = mapping.getMappingsJson();
            ResourceFieldMappingAudit audit = ResourceFieldMappingAudit.readFromJson(mappingJson);
            String resourceVersion = mapping.getVersion();

            for (Long key: audit.getAudits().keySet()) {
                ResourceFieldMappingAudit.ResourceFieldMappingAuditRow row = audit.getAudits().get(key);
                long auditRowId = row.getAuditId();

                for (ResourceFieldMappingAudit.ResourceFieldMappingAuditCol col: row.getCols()) {
                    String field = col.getField();
                    int colIndex = col.getCol();

                    //if we're only interested in a specific field, then skip everything else
                    if (specificField != null
                            && !specificField.equalsIgnoreCase(field)) {
                        continue;
                    }

                    //if we've already found a mapping for this field, ensure that this mapping is for the same version, otherwise skip
                    String version = hmFieldVersions.get(field);
                    if (version != null
                            && !version.equals(resourceVersion)) {
                        continue;
                    }

                    hmFieldVersions.put(field, version);

                    RdbmsSourceFileRecord fileRecord = hmFileRecords.get(new Long(auditRowId));
                    RdbmsSourceFile sourceFile = hmSourceFiles.get(new Integer(fileRecord.getSourceFileId()));

                    String[] valueElements = fileRecord.getValue().split("\\" + CSV_DELIM); //need to escape the delimiter so it's not a regex
                    String value = valueElements[colIndex];

                    ResourceFieldMapping obj = new ResourceFieldMapping();
                    obj.setResourceId(UUID.fromString(mapping.getResourceId()));
                    obj.setResourceType(mapping.getResourceType());
                    obj.setCreatedAt(mapping.getCreatedAt());
                    obj.setVersion(UUID.fromString(mapping.getVersion()));
                    obj.setResourceField(field);
                    obj.setSourceFileName(sourceFile.getFilePath());
                    try {
                        obj.setSourceFileRow(Integer.parseInt(fileRecord.getSourceLocation()));
                    } catch (NumberFormatException nfe) {
                        obj.setSourceLocation(fileRecord.getSourceLocation());
                    }
                    obj.setSourceFileColumn(new Integer(colIndex));
                    obj.setValue(value);

                    ret.add(obj);
                }
            }
        }

        return ret;
    }

    private List<RdbmsSourceFile> findSourceFiles(EntityManager entityManager, List<Integer> distinctFileIds) {

        String sql = "select c"
                + " from"
                + " RdbmsSourceFile c"
                + " where c.id IN :id_list";

        Query query = entityManager.createQuery(sql, RdbmsSourceFile.class)
                .setParameter("id_list", distinctFileIds);

        return query.getResultList();
    }


    private List<RdbmsResourceFieldMappings> findResourceMappings(EntityManager entityManager, ResourceType resourceType, UUID resourceId) throws Exception {

        String sql = "select c"
                + " from"
                + " RdbmsResourceFieldMappings c"
                + " where c.resourceId = :resource_id"
                + " and c.resourceType = :resource_type";

        Query query = entityManager.createQuery(sql, RdbmsResourceFieldMappings.class)
                .setParameter("resource_id", resourceId.toString())
                .setParameter("resource_type", resourceType.toString());

        List<RdbmsResourceFieldMappings> ret = query.getResultList();

        //we always need the mappings in date order, since we only need to retrieve the most recent audit for
        //each JSON field in the resource
        ret.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        return ret;
    }

    private List<RdbmsSourceFileRecord> findSourceFileRecords(EntityManager entityManager,  List<Long> auditRowIds) throws Exception {

        String sql = "select c"
                + " from"
                + " RdbmsSourceFileRecord c"
                + " where c.id IN :audit_ids";

        Query query = entityManager.createQuery(sql, RdbmsSourceFileRecord.class)
                .setParameter("audit_ids", auditRowIds);

        return query.getResultList();
    }

    private List<Integer> findDistinctFileIds(List<RdbmsSourceFileRecord> fileRecords) {
        Set<Integer> fileIds = new HashSet<>();

        for (RdbmsSourceFileRecord fileRecord: fileRecords) {
            int fileId = fileRecord.getSourceFileId();
            fileIds.add(new Integer(fileId));
        }

        return new ArrayList<>(fileIds);
    }

    private List<Long> findDistinctAuditRowIds(List<RdbmsResourceFieldMappings> resourceMappings, String specificField) throws Exception {

        Set<Long> auditRowIds = new HashSet<>();

        Set<String> fieldsDoneSet = new HashSet<>();

        for (int i=resourceMappings.size()-1; i>=0; i--) {
            RdbmsResourceFieldMappings mapping = resourceMappings.get(i);
            String mappingJson = mapping.getMappingsJson();
            ResourceFieldMappingAudit audit = ResourceFieldMappingAudit.readFromJson(mappingJson);

            for (Long key: audit.getAudits().keySet()) {
                ResourceFieldMappingAudit.ResourceFieldMappingAuditRow row = audit.getAudits().get(key);
                long auditRowId = row.getAuditId();
                for (ResourceFieldMappingAudit.ResourceFieldMappingAuditCol col: row.getCols()) {
                    String field = col.getField();

                    if (!fieldsDoneSet.contains(field)
                            && (specificField == null
                            || field.equalsIgnoreCase(specificField))) {
                        fieldsDoneSet.add(field);
                        auditRowIds.add(auditRowId);
                    }
                }
            }
        }

        return new ArrayList<>(auditRowIds);
    }

    /*public List<ResourceFieldMapping> findFieldMappings(UUID serviceId, ResourceType resourceType, UUID resourceId) throws Exception {

        List<ResourceFieldMapping> ret = new ArrayList<>();

        String sql = "SELECT m.resource_id, m.resource_type, m.created_at, m.version, m.resource_field, s.file_path, f.row_index, f.column_index, f.source_location, f.value"
                + " FROM resource_field_mappings m"
                + " INNER JOIN source_file_record f"
                + " ON m.source_file_field_id = f.id"
                + " INNER JOIN source_file s"
                + " ON s.id = f.source_file_id"
                + " WHERE m.resource_id = ?"
                + " AND m.resource_type = ?";

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            ps = connection.prepareStatement(sql);

            ps.setString(1, resourceId.toString());
            ps.setString(2, resourceType.toString());

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                ResourceFieldMapping mapping = getResourceFieldMapping(resultSet);
                ret.add(mapping);
            }

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }

        return ret;
    }*/

    /*private ResourceFieldMapping getResourceFieldMapping(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("resource_id");
        String type = resultSet.getString("resource_type");
        Date created = resultSet.getDate("created_at");
        String version = resultSet.getString("version");
        String resourceField = resultSet.getString("resource_field");
        String filename = resultSet.getString("file_path");
        Integer row = resultSet.getInt("row_index");
        Integer column = resultSet.getInt("column_index");
        String location = resultSet.getString("source_location");
        String value = resultSet.getString("value");

        ResourceFieldMapping mapping = new ResourceFieldMapping();
        mapping.setResourceId(UUID.fromString(id));
        mapping.setResourceType(type);
        mapping.setCreatedAt(created);
        mapping.setVersion(UUID.fromString(version));
        mapping.setResourceField(resourceField);
        mapping.setSourceFileName(filename);
        mapping.setSourceFileRow(row);
        mapping.setSourceFileColumn(column);
        mapping.setSourceLocation(location);
        mapping.setValue(value);
        return mapping;
    }

    public ResourceFieldMapping findFieldMappingForField(UUID serviceId, ResourceType resourceType, UUID resourceId, String field) throws Exception {

        ResourceFieldMapping ret = null;

        String sql = "SELECT m.resource_id, m.resource_type, m.created_at, m.version, m.resource_field, s.file_path, f.row_index, f.column_index, f.source_location, f.value"
            + " FROM resource_field_mapping m"
            + " INNER JOIN source_file_field f"
            + " ON m.source_file_field_id = f.id"
            + " INNER JOIN source_file s"
            + " ON s.id = f.source_file_id"
            + " WHERE m.resource_id = ?"
            + " AND m.resource_type = ?"
            + " AND m.resource_field = ?";

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        PreparedStatement ps = null;
        try {
            SessionImpl session = (SessionImpl)entityManager.getDelegate();
            Connection connection = session.connection();

            ps = connection.prepareStatement(sql);

            ps.setString(1, resourceId.toString());
            ps.setString(2, resourceType.toString());
            ps.setString(3, field);

            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                ret = getResourceFieldMapping(resultSet);
            }

        } finally {
            if (ps != null) {
                ps.close();
            }
            entityManager.close();
        }

        return ret;
    }*/

    public void saveResourceMappings(UUID serviceId, ResourceWrapper resourceWrapper, ResourceFieldMappingAudit audit) throws Exception {

        String mappingJson = audit.writeToJson();

        EntityManager entityManager = ConnectionManager.getPublisherTransformEntityManager(serviceId);
        try {

            RdbmsResourceFieldMappings dbObj = new RdbmsResourceFieldMappings();
            dbObj.setResourceId(resourceWrapper.getResourceId().toString());
            dbObj.setResourceType(resourceWrapper.getResourceType());
            dbObj.setCreatedAt(resourceWrapper.getCreatedAt());
            dbObj.setVersion(resourceWrapper.getVersion().toString());
            dbObj.setMappingsJson(mappingJson);

            entityManager.getTransaction().begin();
            entityManager.persist(dbObj);
            entityManager.getTransaction().commit();

        } finally {
            entityManager.close();
        }
    }
}
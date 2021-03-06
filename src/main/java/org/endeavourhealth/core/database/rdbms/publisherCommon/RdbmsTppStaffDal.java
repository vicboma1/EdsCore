package org.endeavourhealth.core.database.rdbms.publisherCommon;

import org.apache.commons.io.FilenameUtils;
import org.endeavourhealth.common.utility.FileHelper;
import org.endeavourhealth.core.database.dal.publisherCommon.TppStaffDalI;
import org.endeavourhealth.core.database.dal.publisherCommon.models.TppStaffMember;
import org.endeavourhealth.core.database.dal.publisherCommon.models.TppStaffMemberProfile;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class RdbmsTppStaffDal implements TppStaffDalI {
    private static final Logger LOG = LoggerFactory.getLogger(RdbmsTppStaffDal.class);

    @Override
    public void updateStaffMemberLookupTable(String filePath, Date dataDate, int publishedFileId) throws Exception {
        long msStart = System.currentTimeMillis();

        //copy the file from S3 to local disk
        File f = FileHelper.copyFileFromStorageToTempDirIfNecessary(filePath);
        filePath = f.getAbsolutePath();

        Connection connection = ConnectionManager.getPublisherCommonNonPooledConnection();
        try {
            //turn on auto commit so we don't need to separately commit these large SQL operations
            connection.setAutoCommit(true);

            //create a temporary table to load the data into
            String tempTableName = ConnectionManager.generateTempTableName(FilenameUtils.getBaseName(filePath));
            //LOG.debug("Loading " + f + " into " + tempTableName);
            String sql = "CREATE TABLE " + tempTableName + " ("
                    + "RowIdentifier int, "
                    + "StaffName varchar(255), "
                    + "StaffUserName varchar(255), "
                    + "NationalIdType varchar(255), "
                    + "IDNational varchar(255), "
                    + "IDSmartCard varchar(255), "
                    + "Obsolete int, "
                    + "RemovedData int, "
                    + "record_number int, " //note this is auto-generated by the bulk load
                    + "key_exists boolean DEFAULT FALSE, "
                    + "CONSTRAINT pk PRIMARY KEY (RowIdentifier), "
                    + "KEY ix_key_exists (key_exists))";
            Statement statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //bulk load temp table, adding record number as we go
            //LOG.debug("Starting bulk load into " + tempTableName);
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            sql = "set @row = 1";
            statement.executeUpdate(sql);
            sql = "LOAD DATA LOCAL INFILE '" + filePath.replace("\\", "\\\\") + "'"
                    + " INTO TABLE " + tempTableName
                    + " FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\\\"'"
                    + " LINES TERMINATED BY '\\r\\n'"
                    + " IGNORE 1 LINES"
                    + " SET record_number = @row:=@row+1";
            statement.executeUpdate(sql);
            statement.close();

            //work out which records already exist in the target table
            //LOG.debug("Finding records that exist in tpp_staff_member");
            sql = "UPDATE " + tempTableName + " s"
                    + " INNER JOIN tpp_staff_member t"
                    + " ON t.row_id = s.RowIdentifier"
                    + " SET s.key_exists = true";
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //insert records into the target table where the staging has new records
            //LOG.debug("Copying new records into target table tpp_staff_member");
            sql = "INSERT IGNORE INTO tpp_staff_member (row_id, staff_name, username, national_id_type, national_id,"
                    + " smartcard_id, published_file_id, published_file_record_number, dt_last_updated)"
                    + " SELECT RowIdentifier,"
                    + " IF(StaffName != '', StaffName, null),"
                    + " IF(StaffUserName != '', StaffUserName, null),"
                    + " IF(NationalIdType != '', NationalIdType, null),"
                    + " IF(IDNational != '', IDNational, null),"
                    + " IF(IDSmartCard != '', IDSmartCard, null),"
                    + publishedFileId + ", record_number, " + ConnectionManager.formatDateString(dataDate, true)
                    + " FROM " + tempTableName
                    + " WHERE key_exists = false";
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //update any records that previously existed, but have a changed term
            //LOG.debug("Updating existing records in target table tpp_staff_member");
            sql = "UPDATE tpp_staff_member t"
                    + " INNER JOIN " + tempTableName + " s"
                    + " ON t.row_id = s.RowIdentifier"
                    + " SET t.staff_name = IF(s.StaffName != '', s.StaffName, null),"
                    + " t.username = IF(s.StaffUserName != '', s.StaffUserName, null),"
                    + " t.national_id_type = IF(s.NationalIdType != '', s.NationalIdType, null),"
                    + " t.national_id = IF(s.IDNational != '', s.IDNational, null),"
                    + " t.smartcard_id = IF(s.IDSmartCard != '', s.IDSmartCard, null),"
                    + " t.published_file_id = " + publishedFileId + ","
                    + " t.published_file_record_number = s.record_number,"
                    + " t.dt_last_updated = " + ConnectionManager.formatDateString(dataDate, true)
                    + " WHERE t.dt_last_updated < " + ConnectionManager.formatDateString(dataDate, true);
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //delete the temp table
            //LOG.debug("Deleting temp table");
            sql = "DROP TABLE " + tempTableName;
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            long msEnd = System.currentTimeMillis();
            LOG.debug("Update of tpp_staff_member Completed in " + ((msEnd-msStart)/1000) + "s");

        } finally {
            //MUST change this back to false
            connection.setAutoCommit(false);
            connection.close();

            //delete the temp file
            FileHelper.deleteFileFromTempDirIfNecessary(f);
        }
    }

    @Override
    public void updateStaffProfileLookupTable(String filePath, Date dataDate, int publishedFileId) throws Exception {
        long msStart = System.currentTimeMillis();

        //copy the file from S3 to local disk
        File f = FileHelper.copyFileFromStorageToTempDirIfNecessary(filePath);
        filePath = f.getAbsolutePath();

        Connection connection = ConnectionManager.getPublisherCommonNonPooledConnection();
        try {
            //turn on auto commit so we don't need to separately commit these large SQL operations
            connection.setAutoCommit(true);

            //create a temporary table to load the data into
            String tempTableName = ConnectionManager.generateTempTableName(FilenameUtils.getBaseName(filePath));
            //LOG.debug("Loading " + f + " into " + tempTableName);
            String sql = "CREATE TABLE " + tempTableName + " ("
                    + "RowIdentifier int, "
                    + "DateProfileCreated varchar(255), "
                    + "IdProfileCreatedBy int, "
                    + "IDStaffMemberProfileRole int, "
                    + "StaffRole varchar(255), "
                    + "DateEmploymentStart varchar(255), "
                    + "DateEmploymentEnd varchar(255), "
                    + "PPAID varchar(255), "
                    + "GPLocalCode varchar(255), "
                    + "IDStaffMember int, "
                    + "IDOrganisation varchar(255), "
                    + "GmpID varchar(255), "
                    + "RemovedData int, "
                    + "record_number int, " //note this is auto-generated by the bulk load
                    + "key_exists boolean DEFAULT FALSE, "
                    + "CONSTRAINT pk PRIMARY KEY (RowIdentifier), "
                    + "KEY ix_key_exists (key_exists))";
            Statement statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //bulk load temp table, adding record number as we go
            //LOG.debug("Starting bulk load into " + tempTableName);
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            sql = "set @row = 1";
            statement.executeUpdate(sql);
            sql = "LOAD DATA LOCAL INFILE '" + filePath.replace("\\", "\\\\") + "'"
                    + " INTO TABLE " + tempTableName
                    + " FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\\\"'"
                    + " LINES TERMINATED BY '\\r\\n'"
                    + " IGNORE 1 LINES"
                    + " SET record_number = @row:=@row+1";
            statement.executeUpdate(sql);
            statement.close();

            //work out which records already exist in the target table
            //LOG.debug("Finding records that exist in tpp_staff_member_profile");
            sql = "UPDATE " + tempTableName + " s"
                    + " INNER JOIN tpp_staff_member_profile t"
                    + " ON t.row_id = s.RowIdentifier"
                    + " SET s.key_exists = true";
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //insert records into the target table where the staging has new records
            //LOG.debug("Copying new records into target table tpp_staff_member_profile");
            sql = "INSERT IGNORE INTO tpp_staff_member_profile (row_id, organisation_id, staff_member_row_id, start_date,"
                    + " end_date, role_name, ppa_id, gp_local_code, gmp_id, removed_data, published_file_id,"
                    + " published_file_record_number, dt_last_updated)"
                    + " SELECT RowIdentifier, IDOrganisation, IDStaffMember,"
                    + " if (DateEmploymentStart != '', STR_TO_DATE(DateEmploymentStart, '%d %b %Y  %H:%i:%s'), null),"
                    + " if (DateEmploymentEnd != '', STR_TO_DATE(DateEmploymentEnd, '%d %b %Y %H:%i:%s'), null),"
                    + " if (StaffRole != '', StaffRole, null),"
                    + " if (PPAID != '', PPAID, null),"
                    + " if (GPLocalCode != '', GPLocalCode, null),"
                    + " if (GmpID != '' AND GmpID != 'Invalid Id', GmpID, null)," //exclude weird invalid code value
                    + " if (RemovedData != '', RemovedData, 0),"
                    + " " + publishedFileId + ", record_number, " + ConnectionManager.formatDateString(dataDate, true)
                    + " FROM " + tempTableName
                    + " WHERE key_exists = false";
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //update any records that previously existed, but have a changed term
            //LOG.debug("Updating existing records in target table tpp_staff_member_profile");
            sql = "UPDATE tpp_staff_member_profile t"
                    + " INNER JOIN " + tempTableName + " s"
                    + " ON t.row_id = s.RowIdentifier"
                    + " SET t.organisation_id = s.IDOrganisation,"
                    + " t.staff_member_row_id = s.IDStaffMember,"
                    + " t.start_date = if (s.DateEmploymentStart != '', STR_TO_DATE(s.DateEmploymentStart, '%d %b %Y  %H:%i:%s'), null),"
                    + " t.end_date = if (s.DateEmploymentEnd != '', STR_TO_DATE(s.DateEmploymentEnd, '%d %b %Y %H:%i:%s'), null),"
                    + " t.role_name = if (s.StaffRole != '', s.StaffRole, null),"
                    + " t.ppa_id = if (s.PPAID != '', s.PPAID, null),"
                    + " t.gp_local_code = if (s.GPLocalCode != '', s.GPLocalCode, null),"
                    + " t.gmp_id = if (s.GmpID != '' AND s.GmpID != 'Invalid Id', s.GmpID, null),"
                    + " t.removed_data = if (s.RemovedData != '', s.RemovedData, 0),"
                    + " t.published_file_id = " + publishedFileId + ","
                    + " t.published_file_record_number = s.record_number,"
                    + " t.dt_last_updated = " + ConnectionManager.formatDateString(dataDate, true)
                    + " WHERE t.dt_last_updated < " + ConnectionManager.formatDateString(dataDate, true);
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            //delete the temp table
            //LOG.debug("Deleting temp table");
            sql = "DROP TABLE " + tempTableName;
            statement = connection.createStatement(); //one-off SQL due to table name, so don't use prepared statement
            statement.executeUpdate(sql);
            statement.close();

            long msEnd = System.currentTimeMillis();
            LOG.debug("Update of tpp_staff_member_profile Completed in " + ((msEnd-msStart)/1000) + "s");

        } finally {
            //MUST change this back to false
            connection.setAutoCommit(false);
            connection.close();

            //delete the temp file
            FileHelper.deleteFileFromTempDirIfNecessary(f);
        }
    }

    @Override
    public Map<TppStaffMemberProfile, TppStaffMember> retrieveRecordsForProfileIds(Set<Integer> hsStaffMemberProfileIds) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {

            List<Integer> staffMemberProfileIds = new ArrayList<>(hsStaffMemberProfileIds);

            String sql = "SELECT"
                    + " p.row_id, p.organisation_id, p.staff_member_row_id, p.start_date, p.end_date, p.role_name,"
                    + " p.ppa_id, p.gp_local_code, p.gmp_id, p.removed_data, p.published_file_id, p.published_file_record_number,"
                    + " s.row_id, s.staff_name, s.username, s.national_id_type, s.national_id, s.smartcard_id,"
                    + " s.published_file_id, s.published_file_record_number"
                    + " FROM tpp_staff_member_profile p"
                    + " INNER JOIN tpp_staff_member s"
                    + " ON s.row_id = p.staff_member_row_id"
                    + " WHERE p.row_id IN (";
            for (int i=0; i<staffMemberProfileIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ")";

            ps = connection.prepareStatement(sql);

            int col = 1;
            for (int i=0; i<staffMemberProfileIds.size(); i++) {
                Integer id = staffMemberProfileIds.get(i);
                ps.setInt(col++, id);
            }

            Map<TppStaffMemberProfile, TppStaffMember> ret = new HashMap<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;

                TppStaffMemberProfile profile = new TppStaffMemberProfile();
                profile.setRowId(rs.getInt(col++));
                profile.setOrganisationId(rs.getString(col++));
                profile.setStaffMemberRowId(rs.getInt(col++));

                java.sql.Timestamp ts = rs.getTimestamp(col++);
                if (ts != null) {
                    profile.setStartDate(new java.util.Date(ts.getTime()));
                }

                ts = rs.getTimestamp(col++);
                if (ts != null) {
                    profile.setEndDate(new java.util.Date(ts.getTime()));
                }

                profile.setRoleName(rs.getString(col++));
                profile.setPpaId(rs.getString(col++));
                profile.setGpLocalCode(rs.getString(col++));
                profile.setGmpId(rs.getString(col++));
                profile.setRemovedData(rs.getBoolean(col++));
                profile.setPublishedFileId(rs.getInt(col++));
                profile.setPublishedFileRecordNumber(rs.getInt(col++));

                TppStaffMember staff = new TppStaffMember();
                staff.setRowId(rs.getInt(col++));
                staff.setStaffName(rs.getString(col++));

                staff.setUsername(rs.getString(col++));
                staff.setNationalIdType(rs.getString(col++));
                staff.setNationalId(rs.getString(col++));
                staff.setSmartcardId(rs.getString(col++));
                staff.setPublishedFileId(rs.getInt(col++));
                staff.setPublishedFileRecordNumber(rs.getInt(col++));

                ret.put(profile, staff);
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }

    @Override
    public Map<Integer, Integer> findProfileIdsForStaffMemberIdsAtOrg(String organisationId, Set<Integer> hsStaffMemberIds) throws Exception {

        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {
            List<Integer> staffMemberIds = new ArrayList<>(hsStaffMemberIds);

            String sql = "SELECT row_id, staff_member_row_id"
                    + " FROM tpp_staff_member_profile"
                    + " WHERE organisation_id = ?"
                    + " AND staff_member_row_id IN (";
            for (int i=0; i<staffMemberIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ") ORDER BY start_date ASC"; //order by start date, so we will put most recent ones in the returned map

            ps = connection.prepareStatement(sql);

            int col = 1;
            ps.setString(col++, organisationId);

            for (int i=0; i<staffMemberIds.size(); i++) {
                Integer id = staffMemberIds.get(i);
                ps.setInt(col++, id);
            }

            Map<Integer, Integer> ret = new HashMap<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;

                int profileId = rs.getInt(col++);
                int staffId = rs.getInt(col++);

                ret.put(new Integer(staffId), new Integer(profileId));
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }

    @Override
    public Map<Integer, List<Integer>> findAllProfileIdsForStaffMemberIds(Set<Integer> hsStaffMemberIds) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {
            List<Integer> staffMemberIds = new ArrayList<>(hsStaffMemberIds);

            String sql = "SELECT row_id, staff_member_row_id"
                    + " FROM tpp_staff_member_profile"
                    + " WHERE staff_member_row_id IN (";
            for (int i=0; i<staffMemberIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ")";

            ps = connection.prepareStatement(sql);

            int col = 1;

            for (int i=0; i<staffMemberIds.size(); i++) {
                Integer id = staffMemberIds.get(i);
                ps.setInt(col++, id);
            }

            Map<Integer, List<Integer>> ret = new HashMap<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;

                int profileId = rs.getInt(col++);
                int staffId = rs.getInt(col++);

                List l = ret.get(new Integer(staffId));
                if (l == null) {
                    l = new ArrayList<>();
                    ret.put(new Integer(staffId), l);
                }
                l.add(new Integer(profileId));
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }

    }

/*

    @Override
    public void updateStaffMemberStagingTable(List<TppStaffMemberStaging> records) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {
            //see https://thewebfellas.com/blog/conditional-duplicate-key-updates-with-mysql/ for an explanation of this syntax
            String sql = "INSERT INTO tpp_staging_staff_member (row_identifier, dt_last_updated, column_data, published_file_id, published_file_record_number)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " column_data = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(column_data), column_data),"
                    + " published_file_id = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(published_file_id), published_file_id),"
                    + " published_file_record_number = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(published_file_record_number), published_file_record_number),"
                    + " dt_last_updated = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(dt_last_updated), dt_last_updated)";
            //NOTE: updating dt_last_updated MUST always be last in the above UPDATE, otherwise it will NOT update any columns after

            ps = connection.prepareStatement(sql);

            for (TppStaffMemberStaging record: records) {

                String json = ObjectMapperPool.getInstance().writeValueAsString(record.getColumnData());

                int col = 1;

                ps.setInt(col++, record.getRowIdentifier());
                ps.setTimestamp(col++, new java.sql.Timestamp(record.getDtLastUpdated().getTime()));
                ps.setString(col++, json);
                ps.setInt(col++, record.getPublishedFileId());
                ps.setInt(col++, record.getPublishedRecordNumber());
                ps.addBatch();
            }

            ps.executeBatch();
            connection.commit();

        } catch (Exception ex) {
            connection.rollback();
            throw ex;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }

    @Override
    public void updateStaffMemberProfileStagingTable(List<TppStaffMemberProfileStaging> records) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {
            //see https://thewebfellas.com/blog/conditional-duplicate-key-updates-with-mysql/ for an explanation of this syntax
            String sql = "INSERT INTO tpp_staging_staff_member_profile (row_identifier, dt_last_updated, staff_member_row_identifier, column_data, published_file_id, published_file_record_number)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " column_data = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(column_data), column_data),"
                    + " staff_member_row_identifier = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(staff_member_row_identifier), staff_member_row_identifier),"
                    + " published_file_id = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(published_file_id), published_file_id),"
                    + " published_file_record_number = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(published_file_record_number), published_file_record_number),"
                    + " dt_last_updated = IF(VALUES(dt_last_updated) > dt_last_updated, VALUES(dt_last_updated), dt_last_updated)";
            //NOTE: updating dt_last_updated MUST always be last in the above UPDATE, otherwise it will NOT update any columns after

            ps = connection.prepareStatement(sql);

            for (TppStaffMemberProfileStaging record: records) {

                String json = ObjectMapperPool.getInstance().writeValueAsString(record.getColumnData());

                int col = 1;

                ps.setInt(col++, record.getRowIdentifier());
                ps.setTimestamp(col++, new java.sql.Timestamp(record.getDtLastUpdated().getTime()));
                ps.setInt(col++, record.getStaffMemberRowIdentifier());
                ps.setString(col++, json);
                ps.setInt(col++, record.getPublishedFileId());
                ps.setInt(col++, record.getPublishedRecordNumber());
                ps.addBatch();
            }

            ps.executeBatch();
            connection.commit();

        } catch (Exception ex) {
            connection.rollback();
            throw ex;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }

    @Override
    public Map<TppStaffMemberProfileStaging, TppStaffMemberStaging> retrieveAllStagingRecordsForProfileIds(Set<Integer> hsStaffMemberProfileIds) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {

            List<Integer> staffMemberProfileIds = new ArrayList<>(hsStaffMemberProfileIds);

            String sql = "SELECT"
                    + " p.row_identifier, p.dt_last_updated, p.column_data, p.published_file_id, p.published_file_record_number,"
                    + " s.row_identifier, s.dt_last_updated, s.column_data, s.published_file_id, s.published_file_record_number"
                    + " FROM tpp_staging_staff_member_profile p"
                    + " INNER JOIN tpp_staging_staff_member s"
                    + " ON s.row_identifier = p.staff_member_row_identifier"
                    + " WHERE p.row_identifier IN (";
            for (int i=0; i<staffMemberProfileIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ")";

            ps = connection.prepareStatement(sql);

            int col = 1;
            for (int i=0; i<staffMemberProfileIds.size(); i++) {
                Integer id = staffMemberProfileIds.get(i);
                ps.setInt(col++, id);
            }

            Map<TppStaffMemberProfileStaging, TppStaffMemberStaging> ret = new HashMap<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;

                int profileId = rs.getInt(col++);
                Date dtProfileUpdated = new java.util.Date(rs.getTimestamp(col++).getTime());
                String profileColumnDataStr = rs.getString(col++);
                int profileFileId = rs.getInt(col++);
                int profileFileRecord = rs.getInt(col++);
                int staffId = rs.getInt(col++);
                Date dtStaffUpdated = new java.util.Date(rs.getTimestamp(col++).getTime());
                String staffColumnDataStr = rs.getString(col++);
                int staffFileId = rs.getInt(col++);
                int staffFileRecord = rs.getInt(col++);

                Map<String, String> profileColumnData = ObjectMapperPool.getInstance().readValue(profileColumnDataStr, new TypeReference<Map<String, String>>() {});
                Map<String, String> staffColumnData = ObjectMapperPool.getInstance().readValue(staffColumnDataStr, new TypeReference<Map<String, String>>() {});

                TppStaffMemberProfileStaging profile = new TppStaffMemberProfileStaging();
                profile.setRowIdentifier(profileId);
                profile.setDtLastUpdated(dtProfileUpdated);
                profile.setStaffMemberRowIdentifier(staffId);
                profile.setColumnData(profileColumnData);
                profile.setPublishedFileId(profileFileId);
                profile.setPublishedRecordNumber(profileFileRecord);

                TppStaffMemberStaging staff = new TppStaffMemberStaging();
                staff.setRowIdentifier(staffId);
                staff.setDtLastUpdated(dtStaffUpdated);
                staff.setColumnData(staffColumnData);
                staff.setPublishedFileId(staffFileId);
                staff.setPublishedRecordNumber(staffFileRecord);

                ret.put(profile, staff);
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }

    @Override
    public Map<Integer, List<Integer>> findStaffMemberProfileIdsForStaffMemberIds(Set<Integer> hsStaffMemberIds) throws Exception {
        Connection connection = ConnectionManager.getPublisherCommonConnection();
        PreparedStatement ps = null;

        try {
            
            List<Integer> staffMemberIds = new ArrayList<>(hsStaffMemberIds);

            String sql = "SELECT row_identifier, staff_member_row_identifier"
                    + " FROM tpp_staging_staff_member_profile"
                    + " WHERE staff_member_row_identifier IN (";
            for (int i=0; i<staffMemberIds.size(); i++) {
                if (i>0) {
                    sql += ", ";
                }
                sql += "?";
            }
            sql += ")";
            
            ps = connection.prepareStatement(sql);
            
            int col = 1;
            for (int i=0; i<staffMemberIds.size(); i++) {
                Integer id = staffMemberIds.get(i);
                ps.setInt(col++, id);
            }

            Map<Integer, List<Integer>> ret = new HashMap<>();
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                col = 1;
                
                int profileId = rs.getInt(col++);
                int staffId = rs.getInt(col++);
                
                List<Integer> l = ret.get(staffId);
                if (l == null) {
                    l = new ArrayList<>();
                    ret.put(staffId, l);
                }
                l.add(profileId);
            }
            
            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }
    }
*/


}

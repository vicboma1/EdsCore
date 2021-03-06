package org.endeavourhealth.core.database.rdbms.audit;

import org.endeavourhealth.common.config.ConfigManager;
import org.endeavourhealth.common.utility.MetricsHelper;
import org.endeavourhealth.core.database.dal.audit.ScheduledTaskAuditDalI;
import org.endeavourhealth.core.database.dal.audit.models.ScheduledTaskAudit;
import org.endeavourhealth.core.database.rdbms.ConnectionManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RdbmsScheduledTaskAuditDal implements ScheduledTaskAuditDalI {

    @Override
    public void auditTaskSuccess(String taskName, String[] taskParameters) throws Exception {
        auditImpl(taskName, taskParameters, true, null);
    }

    @Override
    public void auditTaskFailure(String taskName, String[] taskParameters, Throwable ex) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String err = sw.toString();

        auditTaskFailure(taskName, taskParameters, err);
    }

    @Override
    public void auditTaskFailure(String taskName, String[] taskParameters, String error) throws Exception {
        auditImpl(taskName, taskParameters, false, error);
    }

    private void auditImpl(String taskName, String[] taskParameters, boolean success, String error) throws Exception {

        Date now = new Date();

        String parameterStr = null;
        if (taskParameters != null && taskParameters.length > 0) {
            parameterStr = String.join(", ", taskParameters);
        }

        Connection connection = ConnectionManager.getAuditConnection();
        PreparedStatement ps = null;
        try {
            //this table just stores the latest instance for an application and task name
            String sql = "INSERT INTO scheduled_task_audit_latest"
                    + " (application_name, task_name, task_parameters, timestmp, host_name, success, error_message)"
                    + " VALUES"
                    + " (?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE"
                    + " task_parameters = VALUES(task_parameters),"
                    + " timestmp = VALUES(timestmp),"
                    + " host_name = VALUES(host_name),"
                    + " success = VALUES(success),"
                    + " error_message = VALUES(error_message)";
            ps = connection.prepareStatement(sql);

            int col = 1;
            ps.setString(col++, ConfigManager.getAppId());
            ps.setString(col++, taskName);
            ps.setString(col++, parameterStr);
            ps.setTimestamp(col++, new java.sql.Timestamp(now.getTime()));
            ps.setString(col++, MetricsHelper.getHostName());
            ps.setBoolean(col++, success);
            ps.setString(col++, error);
            ps.executeUpdate();
            ps.close();
            ps = null;

            //could use a trigger to update the history table, but no more difficult to just do this second insert
            sql = "INSERT INTO scheduled_task_audit_history"
                    + " (application_name, task_name, task_parameters, timestmp, host_name, success, error_message)"
                    + " VALUES"
                    + " (?, ?, ?, ?, ?, ?, ?)";
            ps = connection.prepareStatement(sql);

            col = 1;
            ps.setString(col++, ConfigManager.getAppId());
            ps.setString(col++, taskName);
            ps.setString(col++, parameterStr);
            ps.setTimestamp(col++, new java.sql.Timestamp(now.getTime()));
            ps.setString(col++, MetricsHelper.getHostName());
            ps.setBoolean(col++, success);
            ps.setString(col++, error);
            ps.executeUpdate();
            ps.close();
            ps = null;

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
    public List<ScheduledTaskAudit> getLatestAudits() throws Exception {

        Connection connection = ConnectionManager.getAuditConnection();
        PreparedStatement ps = null;
        try {
            //this table just stores the latest instance for an application and task name
            String sql = "SELECT application_name, task_name, task_parameters, timestmp, host_name, success, error_message"
                    + " FROM scheduled_task_audit_latest";
            ps = connection.prepareStatement(sql);

            List<ScheduledTaskAudit> ret = new ArrayList<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                int col = 1;
                ScheduledTaskAudit a = new ScheduledTaskAudit();
                a.setApplicationName(rs.getString(col++));
                a.setTaskName(rs.getString(col++));
                a.setTaskParameters(rs.getString(col++));
                a.setTimestamp(new java.util.Date(rs.getTimestamp(col++).getTime()));
                a.setHostName(rs.getString(col++));
                a.setSuccess(rs.getBoolean(col++));
                a.setErrorMessage(rs.getString(col++));
                ret.add(a);
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
    public List<ScheduledTaskAudit> getHistory(String applicationName, String taskName) throws Exception {

        Connection connection = ConnectionManager.getAuditConnection();
        PreparedStatement ps = null;
        try {
            //this table just stores the latest instance for an application and task name
            String sql = "SELECT application_name, task_name, task_parameters, timestmp, host_name, success, error_message"
                    + " FROM scheduled_task_audit_history"
                    + " WHERE application_name = ?"
                    + " AND task_name = ?"
                    + " ORDER BY timestmp DESC";
            ps = connection.prepareStatement(sql);

            int col = 1;
            ps.setString(col++, applicationName);
            ps.setString(col++, taskName);

            List<ScheduledTaskAudit> ret = new ArrayList<>();

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {

                col = 1;
                ScheduledTaskAudit a = new ScheduledTaskAudit();
                a.setApplicationName(rs.getString(col++));
                a.setTaskName(rs.getString(col++));
                a.setTaskParameters(rs.getString(col++));
                a.setTimestamp(new java.util.Date(rs.getTimestamp(col++).getTime()));
                a.setHostName(rs.getString(col++));
                a.setSuccess(rs.getBoolean(col++));
                a.setErrorMessage(rs.getString(col++));
                ret.add(a);
            }

            return ret;

        } finally {
            if (ps != null) {
                ps.close();
            }
            connection.close();
        }

    }
}

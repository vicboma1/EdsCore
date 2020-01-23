package org.endeavourhealth.core.database.dal.audit.models;

import java.util.Date;

public class ApplicationHeartbeat {
    private String applicationName;
    private String applicationInstanceName;
    private Date timestmp;
    private String hostName;
    private Boolean isBusy;
    private Integer maxHeapMb;
    private Integer currentHeapMb;

    public ApplicationHeartbeat() {
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationInstanceName() {
        return applicationInstanceName;
    }

    public void setApplicationInstanceName(String applicationInstanceName) {
        this.applicationInstanceName = applicationInstanceName;
    }

    public Date getTimestmp() {
        return timestmp;
    }

    public void setTimestmp(Date timestmp) {
        this.timestmp = timestmp;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public Boolean getBusy() {
        return isBusy;
    }

    public void setBusy(Boolean busy) {
        isBusy = busy;
    }

    public Integer getMaxHeapMb() {
        return maxHeapMb;
    }

    public void setMaxHeapMb(Integer maxHeapMb) {
        this.maxHeapMb = maxHeapMb;
    }

    public Integer getCurrentHeapMb() {
        return currentHeapMb;
    }

    public void setCurrentHeapMb(Integer currentHeapMb) {
        this.currentHeapMb = currentHeapMb;
    }


    @Override
    public String toString() {
        return "applicationName [" + applicationName + "], "
                + "applicationInstanceName [" + applicationInstanceName + "], "
                + "timestmp [" + timestmp + "], "
                + "hostName [" + hostName + "], "
                + "isBusy [" + isBusy + "], "
                + "maxHeapMb [" + maxHeapMb + "], "
                + "currentHeapMb [" + currentHeapMb + "]";
    }
}
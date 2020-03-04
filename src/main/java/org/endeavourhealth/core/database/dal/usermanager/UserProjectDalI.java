package org.endeavourhealth.core.database.dal.usermanager;

import org.endeavourhealth.core.database.rdbms.usermanager.models.UserProjectEntity;

import java.util.List;

public interface UserProjectDalI {

    public Boolean checkUserProjectApplicationAccess(String userId,
                                                     String projectId,
                                                     String applicationName) throws Exception;

    public List<UserProjectEntity> getUserProjectEntitiesForProject(String projectId) throws Exception;
    public List<Object[]> getUserProjects(String userId) throws Exception;
    public void setCurrentDefaultProject(String userId, String userProjectId) throws Exception;
    public void changeDefaultProject(String userId, String defaultRoleId, String userProjectId) throws Exception;
    public void removeCurrentDefaultProject(String userId) throws Exception;
    public UserProjectEntity getDefaultProject(String userId) throws Exception;
    public UserProjectEntity getUserProject(String userProjectId) throws Exception;
    public List<UserProjectEntity> getUserProjectEntities(String userId) throws Exception;

}

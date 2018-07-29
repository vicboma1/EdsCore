package org.endeavourhealth.core.database.dal.publisherCommon;

import org.endeavourhealth.core.database.dal.publisherCommon.models.EmisAdminResourceCache;
import org.endeavourhealth.core.database.dal.publisherCommon.models.EmisCsvCodeMap;

import java.util.List;

public interface EmisTransformDalI {

    void save(List<EmisCsvCodeMap> mappings) throws Exception;
    void save(EmisCsvCodeMap mapping) throws Exception;
    EmisCsvCodeMap getMostRecentCode(boolean medication, Long codeId) throws Exception;

    void save(EmisAdminResourceCache resourceCache) throws Exception;
    void delete(EmisAdminResourceCache resourceCache) throws Exception;
    List<EmisAdminResourceCache> getCachedResources(String dataSharingAgreementGuid) throws Exception;
}

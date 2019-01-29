package org.endeavourhealth.core.fhirStorage;

import org.endeavourhealth.common.utility.ThreadPool;
import org.endeavourhealth.common.utility.ThreadPoolError;
import org.endeavourhealth.core.database.dal.DalProvider;
import org.endeavourhealth.core.database.dal.admin.SystemHelper;
import org.endeavourhealth.core.database.dal.admin.models.Service;
import org.endeavourhealth.core.database.dal.audit.ExchangeBatchDalI;
import org.endeavourhealth.core.database.dal.audit.ExchangeDalI;
import org.endeavourhealth.core.database.dal.audit.models.ExchangeBatch;
import org.endeavourhealth.core.database.dal.audit.models.ExchangeEvent;
import org.endeavourhealth.core.database.dal.audit.models.ExchangeTransformAudit;
import org.endeavourhealth.core.database.dal.audit.models.ExchangeTransformErrorState;
import org.endeavourhealth.core.database.dal.eds.PatientSearchDalI;
import org.endeavourhealth.core.database.dal.ehr.ResourceDalI;
import org.endeavourhealth.core.database.dal.ehr.models.ResourceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class FhirDeletionService {
    private static final Logger LOG = LoggerFactory.getLogger(FhirDeletionService.class);

    private final ExchangeBatchDalI exchangeBatchRepository = DalProvider.factoryExchangeBatchDal();
    private final ExchangeDalI auditRepository = DalProvider.factoryExchangeDal();
    private final ResourceDalI resourceRepository = DalProvider.factoryResourceDal();
    private final Service service;
    private List<UUID> systemIds = null;
    private String progress = null;
    private boolean isComplete = false;
    private Map<UUID, List<UUID>> exchangeIdsToDeleteBySystem = null;
    private int countExchangesToDelete = 0;
    private int countBatchesToDelete = 0;
    private int countBatchesDeleted = 0;
    private ThreadPool threadPool = null;

    public FhirDeletionService(Service service) {
        this.service = service;
        this.progress = "Starting";
    }

    public void deleteData() throws Exception {
        LOG.info("Deleting data for service " + service.getName() + " " + service.getId());

        this.systemIds = SystemHelper.findSystemIds(service);
        LOG.trace("Found " + systemIds.size() + " systems for service");

        retrieveExchangeIdsAndCounts();

        if (exchangeIdsToDeleteBySystem.isEmpty()) {
            LOG.trace("No exchanges to delete");
            this.progress = "Aborted - nothing to delete";
            this.isComplete = true;
            return;
        }

        LOG.trace("Found " + countExchangesToDelete + " exchanges with " + countBatchesToDelete + " batches to delete");

        this.threadPool = new ThreadPool(5, 1000);

        //start looping through our exchange IDs, backwards, so we delete data in reverse order
        for (UUID systemId: systemIds) {
            List<UUID> exchangeIdsToDelete = exchangeIdsToDeleteBySystem.get(systemId);
            for (int i=exchangeIdsToDelete.size()-1; i>=0; i--) {
                UUID exchangeId = exchangeIdsToDelete.get(i);
                deleteExchange(exchangeId, systemId);
            }
        }

        List<ThreadPoolError> errors = threadPool.waitAndStop();
        handleErrors(errors);

        //delete from patient_search tables
        this.progress = "Resources deleted - deleting patient_search";
        PatientSearchDalI patientSearchDal = DalProvider.factoryPatientSearchDal();
        patientSearchDal.deleteForService(service.getId());

        //delete the error state object for the service and system
        this.progress = "Resources deleted - deleting transform audits";
        for (UUID systemId: systemIds) {
            ExchangeTransformErrorState summary = auditRepository.getErrorState(service.getId(), systemId);
            if (summary != null) {
                auditRepository.delete(summary);
            }
        }

        this.isComplete = true;
    }

    private void retrieveExchangeIdsAndCounts() throws Exception {

        ExchangeDalI exchangeDal = DalProvider.factoryExchangeDal();

        exchangeIdsToDeleteBySystem = new HashMap<>();

        for (UUID systemId: systemIds) {
            List<UUID> exchangeIds = exchangeDal.getExchangeIdsForService(service.getId(), systemId);
            exchangeIdsToDeleteBySystem.put(systemId, exchangeIds);

            countExchangesToDelete += exchangeIds.size();

            this.countBatchesToDelete = 0;
            for (UUID exchangeId : exchangeIds) {
                List<ExchangeTransformAudit> audits = exchangeDal.getAllExchangeTransformAudits(service.getId(), systemId, exchangeId);
                for (ExchangeTransformAudit audit : audits) {
                    Integer batchesCreated = audit.getNumberBatchesCreated();

                    if (batchesCreated != null) {
                        countBatchesToDelete += batchesCreated.intValue();
                    }
                }
            }
        }
    }


    private void deleteExchange(UUID exchangeId, UUID systemId) throws Exception {

        //get all batches received for each exchange
        List<UUID> batchIds = getBatchIds(exchangeId);
        LOG.trace("Deleting data for exchangeId " + exchangeId + " with " + batchIds.size() + " batches");

        for (UUID batchId : batchIds) {

            countBatchesDeleted ++;
            progress = new DecimalFormat("###.##").format(((double)countBatchesDeleted / (double)countBatchesToDelete) * 100d) + "%";

            UUID serviceId = service.getId();
            List<ResourceWrapper> resourceByExchangeBatchList = resourceRepository.getResourcesForBatch(serviceId, batchId);
            //LOG.trace("Deleting cassandra for BatchId " + batchId + " " + progress + " (" + resourceByExchangeBatchList.size() + " resources)");

            for (ResourceWrapper resource : resourceByExchangeBatchList) {

                //bump the actual delete from the DB into the threadpool
                DeleteResourceTask callable = new DeleteResourceTask(resource);
                List<ThreadPoolError> errors = threadPool.submit(callable);
                handleErrors(errors);
            }
        }

        //mark any subscriberTransform audits as deleted
        List<ExchangeTransformAudit> transformAudits = auditRepository.getAllExchangeTransformAudits(service.getId(), systemId, exchangeId);
        for (ExchangeTransformAudit transformAudit: transformAudits) {
            if (transformAudit.getDeleted() == null) {
                transformAudit.setDeleted(new Date());
                auditRepository.save(transformAudit);
            }
        }

        //add an event to the exchange to say what we did
        ExchangeEvent exchangeEvent = new ExchangeEvent();
        exchangeEvent.setExchangeId(exchangeId);
        exchangeEvent.setTimestamp(new Date());
        exchangeEvent.setEventDesc("All FHIR deleted from database");
        auditRepository.save(exchangeEvent);
    }



    private void handleErrors(List<ThreadPoolError> errors) throws Exception {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        //if we've had multiple exceptions, this will only log the first, but the first exception is the one that's interesting
        for (ThreadPoolError error: errors) {
            Throwable cause = error.getException();
            //the cause may be an Exception or Error so we need to explicitly
            //cast to the right type to throw it without changing the method signature
            if (cause instanceof Exception) {
                throw (Exception)cause;
            } else if (cause instanceof Error) {
                throw (Error)cause;
            }
        }
    }



    private List<UUID> getBatchIds(UUID exchangeId) throws Exception {

        List<ExchangeBatch> batches = exchangeBatchRepository.retrieveForExchangeId(exchangeId);

        return batches
                .stream()
                .map(t -> t.getBatchId())
                .collect(Collectors.toList());
    }

    private List<ExchangeTransformAudit> getExchangeTransformAudits(UUID systemId) throws Exception {

        List<ExchangeTransformAudit> transformAudits = auditRepository.getAllExchangeTransformAudits(service.getId(), systemId);

        //sort the transforms so we delete in DESCENDING order of received exchange
        //and remove any that are already deleted
        return transformAudits
                .stream()
                .sorted((auditOne, auditTwo) -> auditTwo.getStarted().compareTo(auditOne.getStarted()))
                //.filter(t -> t.getDeleted() == null) //include deleted ones, since we want to make sure EVERYTHING is deleted
                .collect(Collectors.toList());
    }


    public String getProgress() {
        return progress;
    }

    public boolean isComplete() {
        return isComplete;
    }


    /**
     * thread pool runnable to actually perform the delete, so we can do them in parallel
     */
    class DeleteResourceTask implements Callable {

        private ResourceWrapper resourceEntry = null;

        public DeleteResourceTask(ResourceWrapper resourceEntry) {
            this.resourceEntry = resourceEntry;
        }

        @Override
        public Object call() throws Exception {
            try {
                resourceRepository.hardDeleteResourceAndAllHistory(resourceEntry);
            } catch (Exception ex) {
                throw new Exception("Exception deleting " + resourceEntry.getResourceType() + " " + resourceEntry.getResourceId(), ex);
            }

            return null;
        }
    }
}

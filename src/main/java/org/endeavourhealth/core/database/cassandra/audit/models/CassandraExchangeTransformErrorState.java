package org.endeavourhealth.core.database.cassandra.audit.models;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import org.endeavourhealth.core.database.dal.audit.models.ExchangeTransformErrorState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Table(keyspace = "audit", name = "exchange_transform_error_state")
public class CassandraExchangeTransformErrorState {

    @PartitionKey(0)
    @Column(name = "service_id")
    private UUID serviceId = null;
    @PartitionKey(1)
    @Column(name = "system_id")
    private UUID systemId = null;
    @Column(name = "exchange_ids_in_error")
    private List<UUID> exchangeIdsInError = new ArrayList<>();

    public CassandraExchangeTransformErrorState() {}

    public CassandraExchangeTransformErrorState(ExchangeTransformErrorState proxy) {
        this.serviceId = proxy.getServiceId();
        this.systemId = proxy.getSystemId();
        this.exchangeIdsInError = proxy.getExchangeIdsInError();
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(UUID systemId) {
        this.systemId = systemId;
    }

    public List<UUID> getExchangeIdsInError() {
        return exchangeIdsInError;
    }

    public void setExchangeIdsInError(List<UUID> exchangeIdsInError) {
        if (exchangeIdsInError == null) {
            this.exchangeIdsInError = new ArrayList<>();
        } else {
            this.exchangeIdsInError = exchangeIdsInError;
        }
    }


}
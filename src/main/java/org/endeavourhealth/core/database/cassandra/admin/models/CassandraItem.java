package org.endeavourhealth.core.database.cassandra.admin.models;

import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import org.endeavourhealth.core.database.dal.admin.models.Item;

import java.util.UUID;

@Table(keyspace = "admin", name = "item")
public class CassandraItem {
    @PartitionKey
    @Column(name = "id")
    private UUID id;
    @ClusteringColumn(0)
    @Column(name = "audit_id")
    private UUID auditId;
    @Column(name = "xml_content")
    private String xmlContent;
    @Column(name = "title")
    private String title;
    @Column(name = "description")
    private String description;
    @Column(name = "is_deleted")
    private Boolean isDeleted;

    public CassandraItem() {}

    public CassandraItem(Item proxy) {
        this.id = proxy.getId();
        this.auditId = proxy.getAuditId();
        this.xmlContent = proxy.getXmlContent();
        this.title = proxy.getTitle();
        this.description = proxy.getDescription();
        this.isDeleted = proxy.isDeleted();
    }



    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAuditId() {
        return auditId;
    }

    public void setAuditId(UUID auditId) {
        this.auditId = auditId;
    }

    public String getXmlContent() {
        return xmlContent;
    }

    public void setXmlContent(String xmlContent) {
        this.xmlContent = xmlContent;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

}
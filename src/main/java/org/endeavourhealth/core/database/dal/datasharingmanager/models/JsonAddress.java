package org.endeavourhealth.core.database.dal.datasharingmanager.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.endeavourhealth.core.database.rdbms.datasharingmanager.models.AddressEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JsonAddress {
    private String uuid = null;
    private String organisationUuid = null;
    private String buildingName = null;
    private String numberAndStreet = null;
    private String locality = null;
    private String city = null;
    private String county = null;
    private String postcode = null;
    private Double lat = null;
    private Double lng = null;
    private boolean geolocationReprocess = false;

    public JsonAddress() {
    }

    public JsonAddress(AddressEntity address) {
        this.uuid = address.getUuid();
        this.organisationUuid = address.getOrganisationUuid();
        this.buildingName = address.getBuildingName();
        this.numberAndStreet = address.getNumberAndStreet();
        this.locality = address.getLocality();
        this.city = address.getCity();
        this.county = address.getCounty();
        this.postcode = address.getPostcode();
        this.lat = address.getLat();
        this.lng = address.getLng();
        this.geolocationReprocess = false;
    }


    /**
     * gets/sets
     */
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getOrganisationUuid() {
        return organisationUuid;
    }

    public void setOrganisationUuid(String organisationUuid) {
        this.organisationUuid = organisationUuid;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public String getNumberAndStreet() {
        return numberAndStreet;
    }

    public void setNumberAndStreet(String numberAndStreet) {
        this.numberAndStreet = numberAndStreet;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public boolean isGeolocationReprocess() {
        return geolocationReprocess;
    }

    public void setGeolocationReprocess(boolean geolocationReprocess) {
        this.geolocationReprocess = geolocationReprocess;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public void setUuidsIfRequired(String organisationUuid) {
        if (this.getOrganisationUuid() == null) {
            this.setOrganisationUuid(organisationUuid);
        }
        if (this.getUuid() == null) {
            this.setUuid(UUID.randomUUID().toString());
        }
    }

    @Override
    public String toString() {
        List<String> components = new ArrayList<>();

        if (buildingName != null && !buildingName.equals("")) {
            components.add(buildingName);
        }
        if (numberAndStreet != null && !numberAndStreet.equals("")) {
            components.add(numberAndStreet);
        }
        if (locality != null && !locality.equals("")) {
            components.add(locality);
        }
        if (city != null && !city.equals("")) {
            components.add(city);
        }
        if (county != null && !county.equals("")) {
            components.add(county);
        }
        if (postcode != null && !postcode.equals("")) {
            components.add(postcode);
        }

        return String.join(", ", components);
    }
}
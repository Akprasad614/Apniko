package com.localcart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * One explicitly reviewed commercial catalogue source can be enabled at a time.
 * Empty/false defaults intentionally disable generic retail imports.
 */
@Component
@ConfigurationProperties(prefix = "apniko.catalogue.import.approved-source")
public class ApprovedCatalogueSourceProperties {

    private String name = "";
    private String url = "";
    private String license = "";
    private String permissionReference = "";
    private boolean rightsVerified;
    private boolean commercialUsePermitted;

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name.trim(); }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url == null ? "" : url.trim(); }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license == null ? "" : license.trim(); }

    public String getPermissionReference() { return permissionReference; }
    public void setPermissionReference(String permissionReference) {
        this.permissionReference = permissionReference == null ? "" : permissionReference.trim();
    }

    public boolean isRightsVerified() { return rightsVerified; }
    public void setRightsVerified(boolean rightsVerified) { this.rightsVerified = rightsVerified; }

    public boolean isCommercialUsePermitted() { return commercialUsePermitted; }
    public void setCommercialUsePermitted(boolean commercialUsePermitted) {
        this.commercialUsePermitted = commercialUsePermitted;
    }

    public boolean isConfiguredAndApproved() {
        return !name.isBlank()
                && !url.isBlank()
                && !license.isBlank()
                && !permissionReference.isBlank()
                && rightsVerified
                && commercialUsePermitted;
    }

    public boolean matches(String sourceName, String sourceUrl, String sourceLicense, String permissionRef) {
        return isConfiguredAndApproved()
                && name.equals(sourceName)
                && url.equals(sourceUrl)
                && license.equals(sourceLicense)
                && permissionReference.equals(permissionRef);
    }
}

package io.jans.configapi.auth;

import com.google.common.base.Preconditions;
import io.jans.as.common.model.registration.Client;
import io.jans.as.common.service.common.EncryptionService;
import io.jans.as.model.common.ScopeType;
import io.jans.as.model.uma.persistence.UmaResource;
import io.jans.ca.rs.protect.Condition;
import io.jans.ca.rs.protect.RsResource;
import io.jans.ca.rs.protect.RsResourceList;

import io.jans.as.persistence.model.Scope;
import io.jans.configapi.service.ClientService;
import io.jans.configapi.service.ScopeService;
import io.jans.configapi.service.UmaResourceService;
import io.jans.configapi.util.Jackson;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

@ApplicationScoped
public class UmaResourceProtectionService {

    public static final String PROTECTION_CONFIGURATION_FILE_NAME = "uma-rs-protect.json";

    @Inject
    Logger log;
    
    @Inject
    EncryptionService encryptionService;

    @Inject
    UmaResourceProtectionCache umaResourceProtectionCache;

    @Inject
    ScopeService scopeService;

    @Inject
    UmaResourceService umaResourceService;
    
    @Inject
    ClientService clientService;

    Collection<RsResource> rsResourceList;

    public UmaResourceProtectionService() {

    }

    public Collection<RsResource> getResourceList() {
        return rsResourceList;
    }

    public void verifyUmaResources() throws Exception {

        // Load the uma resource json
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = loader.getResourceAsStream(PROTECTION_CONFIGURATION_FILE_NAME);

        RsResourceList resourceList = Jackson.createJsonMapper().readValue(inputStream, RsResourceList.class);
        log.debug(
                " \n\n UmaResourceProtectionService::verifyUmaResources() - resourceList = " + resourceList + "\n\n");
        this.rsResourceList = resourceList.getResources();

        log.debug(" \n\n UmaResourceProtectionService::verifyUmaResources() - rsResourceList = "
                + rsResourceList + "\n\n");

        Preconditions.checkNotNull(rsResourceList, "Config Api Resource list cannot be null !!!");

        createScopeIfNeeded();

        createResourceIfNeeded();
      
    }

    private void createScopeIfNeeded() { 
        List<String> rsScopes = null;
        for (RsResource rsResource : rsResourceList) {
            for (Condition condition : rsResource.getConditions()) {
                rsScopes = condition.getScopes();
                for (String scopeName : rsScopes) {

                    // Check in cache
                    if (UmaResourceProtectionCache.getScope(scopeName) != null) {
                        log.debug("Scope - '" + scopeName + "' exists in cache.");
                        break;
                    }

                    // Check in DB
                    List<Scope> scopes = scopeService.searchScopes(scopeName, 2);
                    Scope scope = null;

                    if (scopes != null && !scopes.isEmpty()) {
                        // Fetch existing scope to store in cache
                        scope = scopes.get(0);
                        if (scopes.size() > 1) {
                            log.error(scopes.size() + " UMA Scope with same name.");
                            throw new WebApplicationException("Multiple UMA Scope with same name - " + scopeName,
                                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
                        }
                    }

                    if (scopes == null || scopes.isEmpty()) {
                        log.debug("Scope - '" + scopeName + "' does not exist, hence creating it.");
                        // Scope does not exists hence create Scope
                        scope = new Scope();
                        String inum = UUID.randomUUID().toString();
                        scope.setId(scopeName);
                        scope.setDisplayName(scopeName);
                        scope.setInum(inum);
                        scope.setDn(scopeService.getDnForScope(inum));
                        scope.setScopeType(ScopeType.UMA);
                        scopeService.addScope(scope);
                    }
                    else {
                        //Update resource
                        log.debug("Scope - '" + scopeName + "' already exists, hence updating it.");
                       
                        scope.setId(scopeName);
                        scope.setDisplayName(scopeName);
                        scope.setDn(scopeService.getDnForScope(scope.getInum()));
                        scope.setScopeType(ScopeType.UMA);
                        scopeService.updateScope(scope);
                    }

                    // Add to cache
                    UmaResourceProtectionCache.putScope(scope);
                }
            }
        }
    }

    public void createResourceIfNeeded() {
        log.debug(" \n\n UmaResourceProtectionService::createResourceIfNeeded() - rsResourceList = "
                + rsResourceList + "\n\n");

        Map<String, UmaResource> allResources = UmaResourceProtectionCache.getAllUmaResources();

        for (RsResource rsResource : rsResourceList) {

            for (Condition condition : rsResource.getConditions()) {
                String umaResourceName = condition.getHttpMethods() + ":::" + rsResource.getPath(); 
                
                log.debug(" \n\n UmaResourceProtectionService::createResourceIfNeeded() - umaResourceName = "
                        + umaResourceName + "\n\n");
                // Check in cache
                if (UmaResourceProtectionCache.getUmaResource(umaResourceName) != null) {
                    log.debug("UmaResource - '" + umaResourceName + "' exists in cache.");
                    break;
                }

                // Check in DB
                List<UmaResource> umaResources = umaResourceService.findResourcesByName(umaResourceName, 2);
                log.debug(" \n\n UmaResourceProtectionService::createResourceIfNeeded() - findResources() -> umaResources = "
                        + umaResources + "\n\n");
                
                UmaResource umaResource = null;

                if (umaResources != null && !umaResources.isEmpty()) {
                    // Fetch existing resources to store in cache
                    umaResource = umaResources.get(0);
                    if (umaResources.size() > 1) {
                        log.error(umaResources.size() + " UMA Resource with same name.");
                        throw new WebApplicationException("Multiple UMA Resource with same name - " + umaResourceName,
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
                    }
                }

                // Create Resource
                if (umaResources == null || umaResources.isEmpty()) {
                    log.debug("UmaResource - '" + umaResources + "' does not exist, hence creating it.");
                    umaResource = new UmaResource();
                    String id = UUID.randomUUID().toString();
                    umaResource.setId(id);
                    umaResource.setDn(umaResourceService.getDnForResource(id));
                    umaResource.setName(umaResourceName);
                    umaResource.setScopes(condition.getScopes()); 
                    umaResource.setCreationDate(getCreationDate(rsResource));
                    umaResource.setDescription("Config API Resource - "+umaResourceName);
                    
                   umaResourceService.addResource(umaResource);
                }
                else {
                    //Update resource
                    log.debug("UmaResource - '" + umaResources + "' already exists, hence updating it.");
                    //umaResource.setDn(umaResource.getId());
                    umaResource.setName(umaResourceName);
                    umaResource.setScopes(condition.getScopes());
                   
                   umaResourceService.updateResource(umaResource);
                }

                // Add to cache
                UmaResourceProtectionCache.putUmaResource(umaResourceName, umaResource);
            }
        }
    }
    
    private Date getCreationDate(RsResource rsResource) {
        final Calendar calendar = Calendar.getInstance();
        Date iat = calendar.getTime();

        if (rsResource.getIat() != null && rsResource.getIat() > 0) {
            iat = new Date(rsResource.getIat() * 1000L);
        }

        return iat;
    }
   
    
    
}

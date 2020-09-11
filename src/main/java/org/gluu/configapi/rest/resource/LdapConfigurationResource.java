package org.gluu.configapi.rest.resource;

import java.util.List;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PathParam;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;

import org.gluu.model.ldap.GluuLdapConfiguration;
import org.gluu.oxtrust.service.LdapConfigurationService;
import org.gluu.configapi.filters.ProtectedApi;
import org.gluu.configapi.util.ApiConstants;
import org.gluu.configapi.util.ConnectionStatus;
import org.gluu.configapi.util.Jackson;

@Path(ApiConstants.BASE_API_URL + ApiConstants.CONFIG + ApiConstants.DATABASE + ApiConstants.LDAP)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LdapConfigurationResource extends BaseResource {
  
  @Inject
  Logger logger;
  
  @Inject
  LdapConfigurationService ldapConfigurationService;
  
  /*
   * @Inject ConnectionStatus connectionStatus;
   */
  
  @GET
  @ProtectedApi( scopes = {READ_ACCESS} )
  public Response getLdapConfiguration() {
     List<GluuLdapConfiguration> ldapConfigurationList = this.ldapConfigurationService.findLdapConfigurations();
     return Response.ok(ldapConfigurationList).build();
  }  
  
  @GET
  @Path(ApiConstants.NAME_PARAM_PATH)
  @ProtectedApi( scopes = {READ_ACCESS} )
  public Response getLdapConfigurationByName(@PathParam(ApiConstants.NAME) String name) {
    GluuLdapConfiguration ldapConfiguration = findLdapConfigurationByName(name);
    return Response.ok(ldapConfiguration).build();
  }
  
  @POST
  @ProtectedApi( scopes = {WRITE_ACCESS} )
  public Response addLdapConfiguration(@Valid @NotNull  GluuLdapConfiguration ldapConfiguration) {
    this.ldapConfigurationService.save(ldapConfiguration);
    return Response.status(Response.Status.CREATED).entity(ldapConfiguration).build();  
  }
    
  @PUT
  @ProtectedApi( scopes = {WRITE_ACCESS} )
  public Response updateLdapConfiguration(@Valid @NotNull  GluuLdapConfiguration ldapConfiguration) {
    findLdapConfigurationByName(ldapConfiguration.getConfigId());
    this.ldapConfigurationService.update(ldapConfiguration);
    return Response.ok(ldapConfiguration).build();  
  }
  
  @DELETE
  @Path(ApiConstants.NAME_PARAM_PATH)
  @ProtectedApi( scopes = {WRITE_ACCESS} )
  public Response deleteLdapConfigurationByName(@PathParam(ApiConstants.NAME) String name) {
    findLdapConfigurationByName(name);
    logger.info("Delete Ldap Configuration by name " + name);
    this.ldapConfigurationService.remove(name);
    return Response.noContent().build();
  }
  
  
  @PATCH
  @Path(ApiConstants.NAME_PARAM_PATH)
  @Consumes(MediaType.APPLICATION_JSON_PATCH_JSON)
  @ProtectedApi(scopes = { WRITE_ACCESS })
  public Response patchLdapConfigurationByName(@PathParam(ApiConstants.NAME) String name, @NotNull String requestString) throws Exception {
    GluuLdapConfiguration ldapConfiguration = findLdapConfigurationByName(name);
    logger.info("Patch Ldap Configuration by name " + name);
    ldapConfiguration = Jackson.applyPatch(requestString, ldapConfiguration);
    this.ldapConfigurationService.update(ldapConfiguration);
      return Response.ok(ldapConfiguration).build();
  }
  
  @POST
  @Path(ApiConstants.TEST)
  @ProtectedApi(scopes = { READ_ACCESS })
  public Response testLdapConfigurationByName(@Valid @NotNull  GluuLdapConfiguration ldapConfiguration) throws Exception {
    System.out.println("\n\n\n LdapConfigurationResource:::testLdapConfigurationByName() - ldapConfiguration = "+ldapConfiguration+"\n\n\n");
    logger.info("Test Ldap Configuration " + ldapConfiguration);
    ConnectionStatus connectionStatus = new ConnectionStatus();
    boolean status = connectionStatus.isUp(ldapConfiguration);
    System.out.println("\n\n\n LdapConfigurationResource:::testLdapConfigurationByName() - status = "+status+"\n\n\n");
    return Response.ok(status).build();
  }
      
  
  private GluuLdapConfiguration findLdapConfigurationByName(String name) {
   try{
      return this.ldapConfigurationService.findLdapConfigurationByName(name);
    }
    catch(Exception ex) {
      logger.error("Could not find Ldap Configuration by name '"+ name+"'", ex);
      throw new NotFoundException(getNotFoundError("Ldap Configuration - '"+name+"'"));
      
    }
  }
  
  

}

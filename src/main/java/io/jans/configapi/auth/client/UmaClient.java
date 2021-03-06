/*
 * Janssen Project software is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2020, Janssen Project
 */

package io.jans.configapi.auth.client;

import io.jans.as.client.TokenResponse;
import io.jans.as.client.TokenRequest;
import io.jans.as.client.uma.UmaRptIntrospectionService;
import io.jans.as.model.common.AuthenticationMethod;
import io.jans.as.model.common.GrantType;
import io.jans.as.model.uma.RptIntrospectionResponse;
import io.jans.as.model.uma.UmaMetadata;
import io.jans.as.model.uma.UmaScopeType;
import io.jans.as.model.uma.wrapper.Token;
import io.jans.as.model.util.Util;
import io.jans.as.client.TokenClient;
import io.jans.as.model.crypto.signature.SignatureAlgorithm;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.client.Invocation.Builder;


//for testing 
import io.jans.configapi.auth.service.UmaService;
import io.jans.as.client.uma.UmaPermissionService;
import io.jans.as.client.uma.UmaRptIntrospectionService;
import io.jans.as.model.uma.PermissionTicket;
import io.jans.as.model.uma.UmaMetadata;
import io.jans.as.model.uma.UmaPermission;
import io.jans.as.model.uma.UmaPermissionList;

import org.slf4j.Logger;

public class UmaClient {

    @Inject
    @RestClient
    UMATokenService service;
    
    @Inject
    UmaService umaService;

    public static Token requestPat(final String tokenUrl, final String umaClientId, final String umaClientSecret,
            String... scopeArray) throws Exception {
        return request(tokenUrl, umaClientId, umaClientSecret, UmaScopeType.PROTECTION, scopeArray);
    }

    public static Token request(final String tokenUrl, final String umaClientId, final String umaClientSecret,
            UmaScopeType scopeType, String... scopeArray) throws Exception {

        String scope = scopeType.getValue();
        if (scopeArray != null && scopeArray.length > 0) {
            for (String s : scopeArray) {
                scope = scope + " " + s;
            }
        }

        TokenResponse tokenResponse = executetPatRequest(tokenUrl, umaClientId, umaClientSecret, scope);

        if (tokenResponse != null) {

            final String patToken = tokenResponse.getAccessToken();
            final Integer expiresIn = tokenResponse.getExpiresIn();
            if (Util.allNotBlank(patToken)) {
                return new Token(null, null, patToken, scopeType.getValue(), expiresIn);
            }
        }
        return null;
    }

    public static TokenResponse executetPatRequest(final String tokenUrl, final String clientId,
            final String clientSecret, final String scope) {

        Builder request = ResteasyClientBuilder.newClient().target(tokenUrl).request();
        TokenRequest tokenRequest = new TokenRequest(GrantType.CLIENT_CREDENTIALS);
        tokenRequest.setScope(scope);
        tokenRequest.setAuthUsername(clientId);
        tokenRequest.setAuthPassword(clientSecret);
        tokenRequest.setAuthenticationMethod(AuthenticationMethod.CLIENT_SECRET_BASIC);

        request.header("Authorization", "Basic " + tokenRequest.getEncodedCredentials());
        request.header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

        ApacheHttpClient43Engine engine = ClientFactory.createEngine(false);
        RestClientBuilder restClient = RestClientBuilder.newBuilder().baseUri(UriBuilder.fromPath(tokenUrl).build())
                .register(engine);
        restClient.property("Authorization", "Basic " + tokenRequest.getEncodedCredentials());
        restClient.property("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

        ResteasyWebTarget target = (ResteasyWebTarget) ResteasyClientBuilder.newClient(restClient.getConfiguration())
                .target(tokenUrl);

        
        Response response = request
                .post(Entity.form(new MultivaluedHashMap<String, String>(tokenRequest.getParameters())));
        
        if (response.getStatus() == 200) {
            String entity = response.readEntity(String.class);
            
            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setEntity(entity);
            tokenResponse.injectDataFromJson(entity);
            
            return tokenResponse;
        }
        return null;

    }
    
    public static TokenResponse requestRpt(final String tokenUrl, final String clientId,
            final String clientSecret, final List<String> scopes, final String ticket) {
      
        String scope = null;
        if (scopes != null && scopes.size() > 0) {
            for (String s : scopes) {
                scope = scope + " " + s;
            }
        }
                
        Builder request = ResteasyClientBuilder.newClient().target(tokenUrl).request();       
        TokenRequest tokenRequest = new TokenRequest(GrantType.OXAUTH_UMA_TICKET);
        tokenRequest.setScope(scope);
        tokenRequest.setAuthUsername(clientId);
        tokenRequest.setAuthPassword(clientSecret);
        tokenRequest.setAuthenticationMethod(AuthenticationMethod.CLIENT_SECRET_BASIC);
        
        final MultivaluedHashMap<String, String> multivaluedHashMap = new MultivaluedHashMap(tokenRequest.getParameters());
        multivaluedHashMap.add("ticket",ticket);
        request.header("Authorization", "Basic " + tokenRequest.getEncodedCredentials());
        request.header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

        ApacheHttpClient43Engine engine = ClientFactory.createEngine(false);
        RestClientBuilder restClient = RestClientBuilder.newBuilder().baseUri(UriBuilder.fromPath(tokenUrl).build())
                .register(engine);
        restClient.property("Authorization", "Basic " + tokenRequest.getEncodedCredentials());
        restClient.property("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

        ResteasyWebTarget target = (ResteasyWebTarget) ResteasyClientBuilder.newClient(restClient.getConfiguration())
                .target(tokenUrl);
        Response response = request.post(Entity.form(multivaluedHashMap));
        
        if (response.getStatus() == 200) {
            String entity = response.readEntity(String.class);
            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setEntity(entity);
            tokenResponse.injectDataFromJson(entity);
            return tokenResponse;
        }
        return null;

    }


    public static RptIntrospectionResponse getRptStatus(UmaMetadata umaMetadata, String authorization,
            String rptToken) {
                ApacheHttpClient43Engine engine = ClientFactory.createEngine(false);
        RestClientBuilder restClient = RestClientBuilder.newBuilder()
                .baseUri(UriBuilder.fromPath(umaMetadata.getIntrospectionEndpoint()).build())
                .property("Content-Type", MediaType.APPLICATION_JSON).register(engine);
        restClient.property("Authorization", "Basic " + authorization);
        restClient.property("Content-Type", MediaType.APPLICATION_JSON);

        ResteasyWebTarget target = (ResteasyWebTarget) ResteasyClientBuilder.newClient(restClient.getConfiguration())
                .property("Content-Type", MediaType.APPLICATION_JSON).target(umaMetadata.getIntrospectionEndpoint());
                
        UmaRptIntrospectionService proxy = target.proxy(UmaRptIntrospectionService.class);
        RptIntrospectionResponse response = proxy.requestRptStatus(authorization, rptToken, "");
  
        return response;
    }

}

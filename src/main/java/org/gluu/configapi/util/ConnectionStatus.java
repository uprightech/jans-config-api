package org.gluu.configapi.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import org.slf4j.Logger;

import org.gluu.model.ldap.GluuLdapConfiguration;
import org.gluu.model.SimpleProperty;
import org.gluu.persist.ldap.operation.impl.LdapConnectionProvider;
import org.gluu.util.security.PropertiesDecrypter;
import org.gluu.configapi.configuration.ConfigurationFactory;


@ApplicationScoped
public class ConnectionStatus {
  
  @Inject
  Logger logger;
  
  @Inject
  ConfigurationFactory configurationFactory;
  

  public boolean isUp(GluuLdapConfiguration ldapConfiguration) {
 
    Properties properties = new Properties();
    properties.setProperty("bindDN", ldapConfiguration.getBindDN());
    properties.setProperty("bindPassword", ldapConfiguration.getBindPassword());
    properties.setProperty("servers", buildServersString(getServers(ldapConfiguration)));
    logger.info("\n\n\n ConnectionStatus:::isUp() - configurationFactory = "+configurationFactory+"\n\n\n");
    logger.info("\n\n\n ConnectionStatus:::isUp() - configurationFactory.getCryptoConfigurationSalt() = "+configurationFactory.getCryptoConfigurationSalt()+"\n\n\n");
    //LdapConnectionProvider connectionProvider = new LdapConnectionProvider(properties);
    LdapConnectionProvider connectionProvider = new LdapConnectionProvider(PropertiesDecrypter.decryptProperties(properties, configurationFactory.getCryptoConfigurationSalt()));

    
    if (connectionProvider.getConnectionPool() != null) {
      boolean isConnected = connectionProvider.isConnected();
      logger.info("\n\n\n ConnectionStatus:::isUp() - isConnected_1 = "+isConnected+"\n\n\n");
      connectionProvider.closeConnectionPool();
      logger.info("\n\n\n ConnectionStatus:::isUp() - isConnected_2 = "+isConnected+"\n\n\n");
      return isConnected;
     }
   
     return false;
  }
  
  
  private List<String> getServers(GluuLdapConfiguration ldapConfiguration){
    logger.info("\n\n\n ConnectionStatus:::getServers() - ldapConfiguration.getServers() = "+ldapConfiguration.getServers()+"\n\n\n");
    List<String> servers = new ArrayList<String>();
    for(SimpleProperty server : ldapConfiguration.getServers())
      servers.add(server.getValue());  
    logger.info("\n\n\n ConnectionStatus:::getServers() - servers = "+servers+"\n\n\n");
    return servers; 
  }


  private String buildServersString(List<String> servers) {
    if (servers == null) {
      return EMPTY;
    }

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String server : servers) {
      if (first) {
        first = false;
       } else {
         sb.append(",");
       }

       sb.append(server);
     }

     return sb.toString();
  }

}
/**
 * @author Meissa
 */
package org.infinispan.playground.embeddedmigration;

import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.configuration.cache.VersioningScheme;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfiguration;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.upgrade.RollingUpgradeManager;


/**
 * @author Meissa
 * This class create a Cache manager instance source, create the HotRod server that will expose the cache for future migration.
 */
public class EmbeddedMigrationSource {
	public static final Logger log = LogManager.getLogger(EmbeddedMigrationSource.class);


	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		Options options = new Options();
	      options.addRequiredOption("c", "config", true, "A configuration file");	    
	      options.addOption("p", "port", true, "The port on which this instance will listen on. Defaults to 11222");
	      options.addOption("b", "bind", true, "The bind address on which this instance will listen on. Defaults to 0.0.0.0");
	      CommandLineParser parser = new DefaultParser();
	      CommandLine cmd = parser.parse(options, args);
	      
	   // Create a cache manager using the supplied configuration
	      DefaultCacheManager cacheManager = new DefaultCacheManager(cmd.getOptionValue("c"));
	      

	      // Configure a cache
	      ConfigurationBuilder builder = new ConfigurationBuilder();
	     
	      
	   // Create the cache(s)
	      cacheManager.defineConfiguration("cache", builder.build());
	      cacheManager.defineConfiguration("cacheTwo", builder.build());
	      
	      Cache<String, Object> cache = cacheManager.getCache("cache");
	      Cache<String, Object> cacheTwo = cacheManager.getCache("cacheTwo");

	      // Create a Hot Rod server which exposes the cache manager for migration to a future instance
	      HotRodServerConfiguration hotRodServerConfiguration = new HotRodServerConfigurationBuilder()
	            .host(cmd.getOptionValue("b", "0.0.0.0"))
	            .port(Integer.parseInt(cmd.getOptionValue("p", "11222")))
	            .build();
	      HotRodServer server = new HotRodServer();
	      server.start(hotRodServerConfiguration, cacheManager);
	      log.info("Listening on hotrod://{}:{}", hotRodServerConfiguration.host(), hotRodServerConfiguration.port());
	      
	      
	   // We fill the cache with data
	         for (int i = 0; i < 100; i++) {
	            cache.put("k" + i, "v" + i);
	         }
	         
	         for (int i = 0; i < 200; i++) {
		            cacheTwo.put("k" + i, "v" + i);
		         }
	         log.info("Filled caches");
	         
	         try {
	             // Wait until ctrl-c
	             log.info("Waiting...");
	             LockSupport.park();
	          } catch (Throwable t) {
	             // Clean up and exit
	             server.stop();
	             cacheManager.stop();
	          }
	

	}

}

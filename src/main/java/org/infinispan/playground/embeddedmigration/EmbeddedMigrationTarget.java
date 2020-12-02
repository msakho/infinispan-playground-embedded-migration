/**
 * @author Meissa
 */
package org.infinispan.playground.embeddedmigration;

import java.util.concurrent.locks.LockSupport;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.impl.HotRodURI;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.upgrade.RollingUpgradeManager;

/**
 * @author Meissa
 * This class runs the target instance, configure the RemoteCacheStore and migrate the entries from the source instance.
 */
public class EmbeddedMigrationTarget {
	public static final Logger log = LogManager.getLogger(EmbeddedMigrationTarget.class);


	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		Options options = new Options();
	      options.addRequiredOption("c", "config", true, "A configuration file");
	      options.addOption("f", "from", true, "A Hot Rod URL pointing to the source instance, e.g. hotrod://127.0.0.1:11222");
	      CommandLineParser parser = new DefaultParser();
	      CommandLine cmd = parser.parse(options, args);
	      
	   // Create a cache manager using the supplied configuration
	      DefaultCacheManager cacheManager = new DefaultCacheManager(cmd.getOptionValue("c"));

	      // Configure a cache
	      ConfigurationBuilder builder = new ConfigurationBuilder();
	      builder.encoding().mediaType(MediaType.APPLICATION_OBJECT_TYPE);

	      boolean doMigration = cmd.hasOption("f");

	      // Migration from a source cluster: configure the remote cache store
	      if (doMigration) {
	         HotRodURI uri = HotRodURI.create(cmd.getOptionValue("f"));
	         RemoteStoreConfigurationBuilder store = builder.persistence().addStore(RemoteStoreConfigurationBuilder.class)
	               .remoteCacheName("cache")
	               .hotRodWrapping(true);
	         uri.getAddresses().forEach(address -> store.addServer().host(address.getHostName()).port(address.getPort()));
	       
	      // Create the cache(s)
		      
		      Cache<String, Object> cache = cacheManager.createCache("cache", builder.build());
		      log.info("CREATING THE TARGET CACHE",cache.getName());
		      
		      for (String cacheName : cacheManager.getCacheNames()) {
		            log.info("Cache {}: Starting migration from {}", cacheName, cmd.getOptionValue("f"));
		            RollingUpgradeManager upgrade = cacheManager.getGlobalComponentRegistry().getNamedComponentRegistry(cacheName).getComponent(RollingUpgradeManager.class);
		            long count = upgrade.synchronizeData("hotrod");
		            log.info("Cache {}: Migrated {} entries", cacheName, count);
		            upgrade.disconnectSource("hotrod");
		            log.info("Cache {}: disconnected from source", cacheName);
		         }

	         
	         
	      }
	   
	      try {
	         // Wait until ctrl-c
	         log.info("Waiting...");
	         LockSupport.park();
	      } catch (Throwable t) {
	         
	         cacheManager.stop();
	      }
	   }
}

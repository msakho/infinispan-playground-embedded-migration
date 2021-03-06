/**
 * @author Meissa
 */
package org.infinispan.playground.embeddedmigration;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.configuration.cache.VersioningScheme;
import org.infinispan.context.Flag;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfiguration;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.infinispan.upgrade.RollingUpgradeManager;



/**
 * @author Meissa
 * This class runs the target instance, configure the RemoteCacheStore and migrate the entries from the source instance.
 * 
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
	      options.addOption("cc", "cache", true, "The cache entries to sychronize");
	      CommandLineParser parser = new DefaultParser();
	      CommandLine cmd = parser.parse(options, args);
	      
	   // Create a cache manager using the supplied configuration
	      DefaultCacheManager cacheManager = new DefaultCacheManager(cmd.getOptionValue("c"));

	      // Configure a cache and enable compatibility versionning.
	      ConfigurationBuilder builder = new ConfigurationBuilder();

	      boolean doMigration = cmd.hasOption("f");
	      
	      // Migration from a source cluster: configure the remote cache store
	      if (doMigration) {
	    	  String targetCacheName=cmd.getOptionValue("cc", "cache");
	         HotRodURI uri = HotRodURI.create(cmd.getOptionValue("f"));
	         RemoteStoreConfigurationBuilder store = builder.persistence().addStore(RemoteStoreConfigurationBuilder.class)
	               .remoteCacheName(targetCacheName)
	               .hotRodWrapping(true)
	               .protocolVersion(ProtocolVersion.PROTOCOL_VERSION_25);
	         
	               
	           
	  
	         uri.getAddresses().forEach(address -> store.addServer().host(address.getHostName()).port(address.getPort()));
	         
	      // Create the cache(s)
	        
	         cacheManager.defineConfiguration(targetCacheName, builder.build());
	         Cache<String, String> cache = cacheManager.getCache(targetCacheName);
	        // Cache<Integer, Integer> cache = cacheManager.getCache(targetCacheName);
	         log.info("CREATING THE TARGET CACHE",cache.getName());
	         
	         for (String cacheName : cacheManager.getCacheNames()) {
	             Configuration cacheConfiguration = cacheManager.getCacheConfiguration(cacheName);
	             Optional<StoreConfiguration> rs = cacheConfiguration.persistence().stores().stream().filter(s -> s instanceof RemoteStoreConfiguration).findAny();
	             if (!rs.isPresent()) {
	                log.info("Skipping cache {}: no RemoteStore present", cacheName);
	                continue;
	             }
	             log.info("Cache {}: Starting migration from {}", cacheName, cmd.getOptionValue("f"));
	             RollingUpgradeManager upgrade = cacheManager.getGlobalComponentRegistry().getNamedComponentRegistry(cacheName).getComponent(RollingUpgradeManager.class);
	             long count = upgrade.synchronizeData("hotrod");
	             log.info("Cache {}: Migrated {} entries", cacheName, count);
	             upgrade.disconnectSource("hotrod");
	             log.info("Cache {}: disconnected from source", cacheName);
	             log.info("Cache migrated size="+cache.size());
	             
	             
	             CacheSet<Entry<String, String>> entries = cache.getAdvancedCache().withFlags(Flag.SKIP_REMOTE_LOOKUP)
	     				.entrySet();
	             for (Entry<String, String> entry : entries) {
					
					String key = entry.getKey();
					String value = entry.getValue();
					
					log.info("CACHE KEY="+key);
					log.info("CACHE VALUE="+value);
					
					
				}
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

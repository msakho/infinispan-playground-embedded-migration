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

public class EmbeddedMigration {
   public static final Logger log = LogManager.getLogger(EmbeddedMigration.class);

   public static void main(String[] args) throws Exception {
      Options options = new Options();
      options.addRequiredOption("c", "config", true, "A configuration file");
      options.addOption("f", "from", true, "A Hot Rod URL pointing to the source instance, e.g. hotrod://127.0.0.1:11222");
      options.addOption("p", "port", true, "The port on which this instance will listen on. Defaults to 11222");
      options.addOption("b", "bind", true, "The bind address on which this instance will listen on. Defaults to 0.0.0.0");
      CommandLineParser parser = new DefaultParser();
      CommandLine cmd = parser.parse(options, args);

      // Create a cache manager using the supplied configuration
      DefaultCacheManager cacheManager = new DefaultCacheManager(cmd.getOptionValue("c"));
      

      // Configure a cache
      ConfigurationBuilder builder = new ConfigurationBuilder();
     // builder.versioning().enable().scheme(VersioningScheme.SIMPLE);
     // builder.compatibility().enable();

      boolean doMigration = cmd.hasOption("f");

      // Migration from a source cluster: configure the remote cache store
      if (doMigration) {
         HotRodURI uri = HotRodURI.create(cmd.getOptionValue("f"));
         RemoteStoreConfigurationBuilder store = builder.persistence().addStore(RemoteStoreConfigurationBuilder.class)
               .remoteCacheName("cache");
             //  .hotRodWrapping(true).protocolVersion("2.5");
         uri.getAddresses().forEach(address -> store.addServer().host(address.getHostName()).port(address.getPort()));
      }

      // Create the cache(s)
      cacheManager.defineConfiguration("cache", builder.build());
      Cache<String, Object> cache = cacheManager.getCache("cache");

      // Create a Hot Rod server which exposes the cache manager for migration to a future instance
      HotRodServerConfiguration hotRodServerConfiguration = new HotRodServerConfigurationBuilder()
            .host(cmd.getOptionValue("b", "0.0.0.0"))
            .port(Integer.parseInt(cmd.getOptionValue("p", "11222")))
            .build();
      HotRodServer server = new HotRodServer();
      server.start(hotRodServerConfiguration, cacheManager);
      log.info("Listening on hotrod://{}:{}", hotRodServerConfiguration.host(), hotRodServerConfiguration.port());

      if (doMigration) {
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
         }
      } else {
         // We fill the cache with data
         for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
         }
         log.info("Filled cache");
      }

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

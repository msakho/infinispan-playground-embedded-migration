Infinispan Embedded Migration
=============================

This project demonstrates how to perform a data migration between applications using an embedded
Infinispan cache manager.

Architecture
------------

Each application instance, which may be a cluster composed of multiple nodes, needs to include a Hot Rod Server which exposes the embedded caches.
When a new application instance is created, each cache that needs to be migrated must be configured with a RemoteCacheStore pointing to the source.
Client traffic must be directed to the target instance before the synchronization process is initiated.
The target instance will retrieve entries from the source instance on-demand.
The synchronization process will then migrate all entries from the source: this process runs in the background and doesn't affect operations on the target.
Once the migration is complete, the RemoteCacheStore is disconnected from the source.
At this point, the source may safely be terminated.


Usage
-----

Compile:

`$ mvn clean package`

Run the first instance (source):

`$ java -classpath target/infinispan-playground-embedded-migration.jar org.infinispan.playground.embeddedmigration.EmbeddedMigrationSource -c src/main/resources/infinispan.xml -b 127.0.0.1
`

Run the second instance (target):

`$  java -classpath target/infinispan-playground-embedded-migration.jar org.infinispan.playground.embeddedmigration.EmbeddedMigrationTarget -c src/main/resources/infinispan.xml  -f hotrod://127.0.0.1:11222`

Once the migration is complete the source instance can be terminated with Ctrl-C.



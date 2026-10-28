Custom DTO libraries
====================

Put the .jar files that contain your application's DTO classes (and, if used,
your DataSerializableFactory / PortableFactory / CompactSerializer /
StreamSerializer implementations) into this directory.

bin/start.sh and bin/start.bat start the service with -Dloader.path=<this dir>,
so every jar here is added to the classpath on start-up. Restart the service
after adding or replacing jars.

With the classes available, the inspector shows your objects field by field
instead of "not deserializable", and you can write values of type "Custom class".
Compact records are readable and editable even without their classes.

Factories and serializers additionally have to be registered in
config/application.yml (admin.hazelcast.serialization.*), with the same ids your
application uses.

Only add libraries you trust: classes in these jars are loaded into the admin
service and used to deserialize data read from the cluster.

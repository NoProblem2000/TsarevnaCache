Custom DTO libraries
====================

Tip: IdentifiedDataSerializable DTOs written with SerializationUtils-style helpers
need no jar at all. Open "DTO Schemas" in the web UI and paste the Java source of
your DataSerializableFactory and DTOs (see README.md, "Working with Custom DTOs").

For everything else, put the .jar files that contain your application's DTO
classes - and the libraries those classes reference - into this directory.
Hazelcast, Jackson and Spring are already part of the service; do not add them.

HazelcastInspector.exe, bin/start.sh and bin/start.bat start the service with
-Dloader.path=lib/, so every jar here is on the classpath. Restart the service
after adding or replacing jars.

DataSerializableFactory and PortableFactory classes with a "static int FACTORY_ID"
constant are found and registered automatically on start (the log shows
"Discovered DataSerializableFactory <id> -> <class>"). Factories without such a
constant, Compact serializers and custom StreamSerializers are registered in
config/application.yml (admin.hazelcast.serialization.*), with the same ids your
application uses.

With the classes available, the inspector shows your objects field by field
instead of "not deserializable", and you can write values of type "Custom class".
Compact records are readable and editable even without their classes.

Only add libraries you trust: classes in these jars are loaded into the admin
service and used to deserialize data read from the cluster.

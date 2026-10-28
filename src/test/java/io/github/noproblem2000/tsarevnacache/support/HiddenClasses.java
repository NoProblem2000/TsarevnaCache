package io.github.noproblem2000.tsarevnacache.support;

import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compiles DTO classes at test runtime into a private directory. They are visible to the embedded
 * cluster member but <b>not</b> to the admin client, reproducing "class missing on the admin
 * classpath" scenarios for every serialization mechanism.
 */
public final class HiddenClasses {

    public static final int IDS_FACTORY_ID = 1000;
    public static final int PORTABLE_FACTORY_ID = 2000;
    public static final int CUSTOM_TYPE_ID = 777;

    private static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry("hidden/HiddenSerializable.java", """
                    package hidden;
                    public class HiddenSerializable implements java.io.Serializable {
                        private static final long serialVersionUID = 1L;
                        private String name;
                        private int age;
                        public HiddenSerializable() { }
                        public HiddenSerializable(String name, int age) { this.name = name; this.age = age; }
                    }
                    """),
            Map.entry("hidden/HiddenExternalizable.java", """
                    package hidden;
                    public class HiddenExternalizable implements java.io.Externalizable {
                        private String name;
                        public HiddenExternalizable() { }
                        public HiddenExternalizable(String name) { this.name = name; }
                        public void writeExternal(java.io.ObjectOutput out) throws java.io.IOException { out.writeUTF(name); }
                        public void readExternal(java.io.ObjectInput in) throws java.io.IOException { name = in.readUTF(); }
                    }
                    """),
            Map.entry("hidden/HiddenAddress.java", """
                    package hidden;
                    public class HiddenAddress {
                        private String city;
                        private int zip;
                        public HiddenAddress() { }
                        public HiddenAddress(String city, int zip) { this.city = city; this.zip = zip; }
                    }
                    """),
            Map.entry("hidden/HiddenCompact.java", """
                    package hidden;
                    public class HiddenCompact {
                        private String name;
                        private int age;
                        private Integer score;
                        private HiddenAddress address;
                        private long[] tags;
                        public HiddenCompact() { }
                        public HiddenCompact(String name, int age, Integer score, HiddenAddress address, long[] tags) {
                            this.name = name; this.age = age; this.score = score; this.address = address; this.tags = tags;
                        }
                    }
                    """),
            Map.entry("hidden/HiddenDataSerializable.java", """
                    package hidden;
                    public class HiddenDataSerializable implements com.hazelcast.nio.serialization.DataSerializable {
                        private String name;
                        public HiddenDataSerializable() { }
                        public HiddenDataSerializable(String name) { this.name = name; }
                        public void writeData(com.hazelcast.nio.ObjectDataOutput out) throws java.io.IOException { out.writeString(name); }
                        public void readData(com.hazelcast.nio.ObjectDataInput in) throws java.io.IOException { name = in.readString(); }
                    }
                    """),
            Map.entry("hidden/HiddenIdentified.java", """
                    package hidden;
                    public class HiddenIdentified implements com.hazelcast.nio.serialization.IdentifiedDataSerializable {
                        private String name;
                        public HiddenIdentified() { }
                        public HiddenIdentified(String name) { this.name = name; }
                        public int getFactoryId() { return %d; }
                        public int getClassId() { return 1; }
                        public void writeData(com.hazelcast.nio.ObjectDataOutput out) throws java.io.IOException { out.writeString(name); }
                        public void readData(com.hazelcast.nio.ObjectDataInput in) throws java.io.IOException { name = in.readString(); }
                    }
                    """.formatted(IDS_FACTORY_ID)),
            Map.entry("hidden/HiddenDataSerializableFactory.java", """
                    package hidden;
                    public class HiddenDataSerializableFactory implements com.hazelcast.nio.serialization.DataSerializableFactory {
                        public com.hazelcast.nio.serialization.IdentifiedDataSerializable create(int typeId) {
                            return typeId == 1 ? new HiddenIdentified() : null;
                        }
                    }
                    """),
            Map.entry("hidden/HiddenPortable.java", """
                    package hidden;
                    @SuppressWarnings("deprecation")
                    public class HiddenPortable implements com.hazelcast.nio.serialization.Portable {
                        private String name;
                        public HiddenPortable() { }
                        public HiddenPortable(String name) { this.name = name; }
                        public int getFactoryId() { return %d; }
                        public int getClassId() { return 1; }
                        public void writePortable(com.hazelcast.nio.serialization.PortableWriter writer) throws java.io.IOException { writer.writeString("name", name); }
                        public void readPortable(com.hazelcast.nio.serialization.PortableReader reader) throws java.io.IOException { name = reader.readString("name"); }
                    }
                    """.formatted(PORTABLE_FACTORY_ID)),
            Map.entry("hidden/HiddenPortableFactory.java", """
                    package hidden;
                    @SuppressWarnings("deprecation")
                    public class HiddenPortableFactory implements com.hazelcast.nio.serialization.PortableFactory {
                        public com.hazelcast.nio.serialization.Portable create(int classId) {
                            return classId == 1 ? new HiddenPortable() : null;
                        }
                    }
                    """),
            Map.entry("hidden/HiddenCustom.java", """
                    package hidden;
                    public class HiddenCustom {
                        private final String name;
                        public HiddenCustom(String name) { this.name = name; }
                        public String name() { return name; }
                    }
                    """),
            Map.entry("hidden/HiddenCustomSerializer.java", """
                    package hidden;
                    public class HiddenCustomSerializer implements com.hazelcast.nio.serialization.StreamSerializer<HiddenCustom> {
                        public int getTypeId() { return %d; }
                        public void write(com.hazelcast.nio.ObjectDataOutput out, HiddenCustom value) throws java.io.IOException { out.writeString(value.name()); }
                        public HiddenCustom read(com.hazelcast.nio.ObjectDataInput in) throws java.io.IOException { return new HiddenCustom(in.readString()); }
                    }
                    """.formatted(CUSTOM_TYPE_ID)));

    private HiddenClasses() {
    }

    /**
     * Compiles all hidden sources and returns a class loader that can load them.
     */
    public static URLClassLoader compile() {
        try {
            Path root = Files.createTempDirectory("tsarevna-hidden");
            Path sources = Files.createDirectories(root.resolve("src"));
            Path classes = Files.createDirectories(root.resolve("classes"));
            List<String> args = new ArrayList<>(List.of(
                    "-d", classes.toString(),
                    "-classpath", hazelcastJar(),
                    "-nowarn", "-Xlint:none", "-proc:none"));
            for (Map.Entry<String, String> source : SOURCES.entrySet()) {
                Path file = sources.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
                args.add(file.toString());
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Tests must run on a JDK (javax.tools compiler not available)");
            }
            int result = compiler.run(null, null, null, args.toArray(String[]::new));
            if (result != 0) {
                throw new IllegalStateException("Compilation of hidden classes failed: " + result);
            }
            return new URLClassLoader("hidden-dto", new URL[] {classes.toUri().toURL()},
                    HiddenClasses.class.getClassLoader());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String hazelcastJar() {
        try {
            return Path.of(HazelcastInstance.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot locate the Hazelcast jar", e);
        }
    }
}

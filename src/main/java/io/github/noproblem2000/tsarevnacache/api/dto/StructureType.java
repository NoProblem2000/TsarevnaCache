package io.github.noproblem2000.tsarevnacache.api.dto;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Distributed object types known to the explorer, identified by their Hazelcast service name.
 */
public enum StructureType {

    MAP("hz:impl:mapService", "map"),
    REPLICATED_MAP("hz:impl:replicatedMapService", "replicatedmap"),
    MULTI_MAP("hz:impl:multiMapService", "multimap"),
    QUEUE("hz:impl:queueService", "queue"),
    SET("hz:impl:setService", "set"),
    LIST("hz:impl:listService", "list"),
    TOPIC("hz:impl:topicService", "topic"),
    RELIABLE_TOPIC("hz:impl:reliableTopicService", "reliabletopic"),
    FLAKE_ID_GENERATOR("hz:impl:flakeIdGeneratorService", "flakeid"),
    OTHER(null, "other");

    private final String serviceName;
    private final String pathName;

    StructureType(String serviceName, String pathName) {
        this.serviceName = serviceName;
        this.pathName = pathName;
    }

    /** Hazelcast service name, {@code null} for {@link #OTHER}. */
    public String serviceName() {
        return serviceName;
    }

    /** Lower case name used in URLs, e.g. {@code replicatedmap}. */
    public String pathName() {
        return pathName;
    }

    public static StructureType fromServiceName(String serviceName) {
        return Arrays.stream(values())
                .filter(type -> type.serviceName != null && type.serviceName.equals(serviceName))
                .findFirst()
                .orElse(OTHER);
    }

    /** Resolves a URL segment such as {@code queue} or {@code REPLICATED_MAP}. */
    public static Optional<StructureType> fromPath(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return Arrays.stream(values()).filter(type -> type.pathName.equals(normalized)).findFirst();
    }
}

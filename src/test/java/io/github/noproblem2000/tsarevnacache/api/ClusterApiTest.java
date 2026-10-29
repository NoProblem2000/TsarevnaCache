package io.github.noproblem2000.tsarevnacache.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClusterApiTest extends AbstractApiTest {

    @Autowired
    private HazelcastInstance hazelcastClient;

    @Test
    void overviewReportsMembersAndMapMemory() throws Exception {
        IMap<String, String> map = hazelcastClient.getMap("overview-heavy");
        for (int i = 0; i < 200; i++) {
            map.put("key-" + i, "x".repeat(500));
        }

        JsonNode overview = awaitJson("/api/cluster/overview",
                body -> body.path("totals").path("mapEntries").asLong() >= 200
                        && contains(body.path("topMaps"), "overview-heavy"));

        assertThat(overview.path("connected").asBoolean()).isTrue();
        assertThat(overview.path("clusterName").asText()).isEqualTo(cluster().clusterName());
        assertThat(overview.path("clientVersion").asText()).isEqualTo("5.5.0");
        assertThat(overview.path("clusterVersion").asText()).isEqualTo("5.5.0");

        JsonNode member = overview.path("members").get(0);
        assertThat(member.path("master").asBoolean()).isTrue();
        assertThat(member.path("liteMember").asBoolean()).isFalse();
        assertThat(member.path("address").asText()).isEqualTo(cluster().address());
        assertThat(member.path("maxHeap").asLong()).isPositive();

        assertThat(overview.path("totals").path("ownedEntryMemoryCost").asLong()).isGreaterThan(100_000);
        // other test classes share the cluster and may hold larger maps: no fixed ranking, no fixed unit
        assertThat(overview.path("totals").path("ownedEntryMemoryCostFormatted").asText()).matches("[0-9.]+ (KB|MB)");
        JsonNode topMaps = overview.path("topMaps");
        assertThat(topMaps.size()).isBetween(1, 5);
        for (int i = 1; i < topMaps.size(); i++) {
            assertThat(topMaps.get(i - 1).path("ownedEntryMemoryCost").asLong())
                    .isGreaterThanOrEqualTo(topMaps.get(i).path("ownedEntryMemoryCost").asLong());
        }
        assertThat(overview.path("structureCounts").path("MAP").asInt()).isPositive();
        assertThat(overview.path("stats").path("available").asBoolean()).isTrue();
    }

    @Test
    void structuresAreGroupedByType() throws Exception {
        hazelcastClient.getMap("registry-map").put("a", "b");
        hazelcastClient.getQueue("registry-queue").offer("q");
        hazelcastClient.getSet("registry-set").add("s");
        hazelcastClient.getList("registry-list").add("l");
        hazelcastClient.getMultiMap("registry-multimap").put("k", "v");
        hazelcastClient.getReplicatedMap("registry-replicated").put("k", "v");
        hazelcastClient.getTopic("registry-topic").publish("t");
        hazelcastClient.getFlakeIdGenerator("registry-ids").newId();
        cluster().member().getMap("__internal-map").put("a", "b");

        JsonNode structures = awaitJson("/api/structures",
                body -> find(body.path("groups").path("MAP"), "registry-map") != null
                        && find(body.path("groups").path("MAP"), "registry-map").path("ownedEntryMemoryCost").asLong() > 0);

        JsonNode groups = structures.path("groups");
        assertThat(find(groups.path("QUEUE"), "registry-queue").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("SET"), "registry-set").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("LIST"), "registry-list").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("MULTI_MAP"), "registry-multimap").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("REPLICATED_MAP"), "registry-replicated").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("TOPIC"), "registry-topic")).isNotNull();
        assertThat(find(groups.path("FLAKE_ID_GENERATOR"), "registry-ids")).isNotNull();
        assertThat(find(groups.path("MAP"), "registry-map").path("size").asLong()).isEqualTo(1);
        assertThat(find(groups.path("MAP"), "__internal-map")).isNull();

        JsonNode withInternal = httpGet("/api/structures?includeInternal=true").body();
        assertThat(find(withInternal.path("groups").path("MAP"), "__internal-map").path("internal").asBoolean())
                .isTrue();
    }

    private static boolean contains(JsonNode maps, String name) {
        List<String> names = new ArrayList<>();
        maps.forEach(map -> names.add(map.path("name").asText()));
        return names.contains(name);
    }

    private static JsonNode find(JsonNode list, String name) {
        for (JsonNode item : list) {
            if (name.equals(item.path("name").asText())) {
                return item;
            }
        }
        return null;
    }
}

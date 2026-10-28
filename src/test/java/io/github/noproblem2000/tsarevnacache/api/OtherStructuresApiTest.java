package io.github.noproblem2000.tsarevnacache.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hazelcast.collection.IList;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ISet;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OtherStructuresApiTest extends AbstractApiTest {

    @Autowired
    private HazelcastInstance hazelcastClient;

    @Test
    void queueItemsCanBeViewedRemovedAndCleared() throws Exception {
        IQueue<Object> queue = cluster().member().getQueue("jobs");
        for (int i = 0; i < 60; i++) {
            queue.offer("job-" + i);
        }
        queue.offer(cluster().newHidden("HiddenSerializable", new Class<?>[] {String.class, int.class}, "x", 1));

        JsonNode view = httpGet("/api/other/queue/jobs").body();
        assertThat(view.path("type").asText()).isEqualTo("QUEUE");
        assertThat(view.path("size").asLong()).isEqualTo(61);
        assertThat(view.path("itemsAvailable").asBoolean()).isTrue();
        assertThat(view.path("items").size()).isEqualTo(50);
        assertThat(view.path("items").get(0).path("value").path("preview").asText()).isEqualTo("job-0");

        JsonNode all = httpGet("/api/other/queue/jobs?limit=500").body();
        assertThat(all.path("items").size()).isEqualTo(50);

        String ref = view.path("items").get(1).path("ref").asText();
        assertThat(httpDelete("/api/other/queue/jobs/item?ref=" + ref).status()).isEqualTo(204);
        assertThat(queue.contains("job-1")).isFalse();
        assertThat(httpDelete("/api/other/queue/jobs/item?ref=" + ref).code()).isEqualTo("ITEM_NOT_FOUND");

        assertThat(httpDelete("/api/other/queue/jobs/clear").code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(httpDelete("/api/other/queue/jobs/clear?confirm=jobs").status()).isEqualTo(204);
        assertThat(queue.size()).isZero();
    }

    @Test
    void unresolvedQueueItemsAreReported() throws Exception {
        IQueue<Object> queue = cluster().member().getQueue("hidden-jobs");
        queue.offer(cluster().newHidden("HiddenSerializable", new Class<?>[] {String.class, int.class}, "x", 1));

        JsonNode item = httpGet("/api/other/queue/hidden-jobs").body().path("items").get(0);
        assertThat(item.path("value").path("deserialized").asBoolean()).isFalse();
        assertThat(item.path("value").path("unresolved").path("missingClass").asText())
                .isEqualTo("hidden.HiddenSerializable");
    }

    @Test
    void setsAndLists() throws Exception {
        ISet<String> set = hazelcastClient.getSet("tags");
        set.add("a");
        set.add("b");
        IList<Integer> list = hazelcastClient.getList("numbers");
        for (int i = 0; i < 100; i++) {
            list.add(i);
        }

        JsonNode setView = httpGet("/api/other/set/tags").body();
        assertThat(setView.path("size").asLong()).isEqualTo(2);
        String ref = setView.path("items").get(0).path("ref").asText();
        String removed = setView.path("items").get(0).path("value").path("preview").asText();
        assertThat(httpDelete("/api/other/set/tags/item?ref=" + ref).status()).isEqualTo(204);
        assertThat(set).doesNotContain(removed).hasSize(1);

        JsonNode listView = httpGet("/api/other/list/numbers?limit=10").body();
        assertThat(listView.path("size").asLong()).isEqualTo(100);
        assertThat(listView.path("items").size()).isEqualTo(10);
        assertThat(listView.path("items").get(9).path("value").path("dataType").asText()).isEqualTo("INTEGER");
        assertThat(httpDelete("/api/other/list/numbers/clear?confirm=numbers").status()).isEqualTo(204);
        assertThat(list).isEmpty();
    }

    @Test
    void multiMapsAndReplicatedMaps() throws Exception {
        MultiMap<String, String> multiMap = hazelcastClient.getMultiMap("groups");
        multiMap.put("admins", "ann");
        multiMap.put("admins", "bob");
        multiMap.put("users", "carl");
        ReplicatedMap<String, String> replicated = hazelcastClient.getReplicatedMap("settings");
        replicated.put("theme", "dark");
        replicated.put("lang", "en");

        JsonNode groups = httpGet("/api/other/multimap/groups").body();
        assertThat(groups.path("type").asText()).isEqualTo("MULTI_MAP");
        assertThat(groups.path("size").asLong()).isEqualTo(3);
        JsonNode bob = null;
        for (JsonNode item : groups.path("items")) {
            if ("bob".equals(item.path("value").path("preview").asText())) {
                bob = item;
            }
        }
        assertThat(bob).isNotNull();
        assertThat(bob.path("key").path("preview").asText()).isEqualTo("admins");
        assertThat(httpDelete("/api/other/multimap/groups/item?keyRef=" + bob.path("keyRef").asText()
                + "&ref=" + bob.path("ref").asText()).status()).isEqualTo(204);
        assertThat(multiMap.get("admins")).containsExactly("ann");
        assertThat(httpDelete("/api/other/multimap/groups/item?keyRef=" + bob.path("keyRef").asText()).status())
                .isEqualTo(204);
        assertThat(multiMap.containsKey("admins")).isFalse();

        JsonNode settings = httpGet("/api/other/replicatedmap/settings").body();
        assertThat(settings.path("size").asLong()).isEqualTo(2);
        JsonNode theme = null;
        for (JsonNode item : settings.path("items")) {
            if ("theme".equals(item.path("key").path("preview").asText())) {
                theme = item;
            }
        }
        assertThat(theme).isNotNull();
        assertThat(theme.path("value").path("preview").asText()).isEqualTo("dark");
        assertThat(httpDelete("/api/other/replicatedmap/settings/item?keyRef=" + theme.path("keyRef").asText())
                .status()).isEqualTo(204);
        assertThat(replicated.containsKey("theme")).isFalse();
    }

    @Test
    void unknownTypesAndStructuresAreRejected() throws Exception {
        assertThat(httpGet("/api/other/topic/anything").code()).isEqualTo("UNSUPPORTED_STRUCTURE_TYPE");
        assertThat(httpGet("/api/other/queue/missing-queue").code()).isEqualTo("STRUCTURE_NOT_FOUND");
        assertThat(httpGet("/api/other/queue/missing-queue").status()).isEqualTo(404);
    }
}

package io.github.noproblem2000.tsarevnacache.api;

import static io.github.noproblem2000.tsarevnacache.support.EmbeddedCluster.field;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.map.IMap;
import io.github.noproblem2000.tsarevnacache.inspector.DataRefs;
import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class MapApiTest extends AbstractApiTest {

    @Autowired
    private HazelcastInstance hazelcastClient;

    @Test
    void cursorScanReturnsEveryEntryExactlyOnce() throws Exception {
        IMap<String, Integer> map = hazelcastClient.getMap("scan-map");
        for (int i = 0; i < 300; i++) {
            map.put("k" + i, i);
        }

        Set<String> seen = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            Response response = cursor == null
                    ? httpGet("/api/maps/scan-map/entries?size=37")
                    : httpGet("/api/maps/scan-map/entries?size=37&cursor=" + cursor);
            assertThat(response.status()).isEqualTo(200);
            JsonNode page = response.body();
            assertThat(page.path("mode").asText()).isEqualTo("ALL");
            assertThat(page.path("total").asLong()).isEqualTo(300);
            assertThat(page.path("entries").size()).isLessThanOrEqualTo(37);
            for (JsonNode entry : page.path("entries")) {
                assertThat(seen.add(entry.path("key").path("preview").asText())).isTrue();
                assertThat(entry.path("value").has("value")).isFalse();
                assertThat(entry.path("value").path("preview").asText()).isNotEmpty();
                assertThat(entry.path("metadata").path("cost").asLong()).isPositive();
            }
            cursor = page.path("nextCursor").asText(null);
            pages++;
        } while (cursor != null && pages < 50);

        assertThat(seen).hasSize(300);
    }

    @Test
    void unresolvedEntriesDoNotBreakThePage() throws Exception {
        IMap<Object, Object> memberMap = cluster().member().getMap("mixed-map");
        memberMap.put("plain", "hello");
        memberMap.put("hidden-value",
                cluster().newHidden("HiddenSerializable", new Class<?>[] {String.class, int.class}, "x", 1));
        Object hiddenKey = cluster().newHidden("HiddenExternalizable", new Class<?>[] {String.class}, "key");
        memberMap.put(hiddenKey, "value-of-hidden-key");

        JsonNode page = httpGet("/api/maps/mixed-map/entries?size=10").body();
        assertThat(page.path("entries").size()).isEqualTo(3);

        JsonNode unresolvedValue = entryByKeyPreview(page, "hidden-value");
        assertThat(unresolvedValue.path("value").path("deserialized").asBoolean()).isFalse();
        assertThat(unresolvedValue.path("value").path("unresolved").path("errorCode").asText())
                .isEqualTo("CLASS_NOT_FOUND");
        assertThat(unresolvedValue.path("value").path("unresolved").path("entryCost").asLong()).isPositive();

        String hiddenKeyRef = DataRefs.encode(cluster().memberSerialization().toData(hiddenKey));
        JsonNode unresolvedKey = null;
        for (JsonNode entry : page.path("entries")) {
            if (hiddenKeyRef.equals(entry.path("keyRef").asText())) {
                unresolvedKey = entry;
            }
        }
        assertThat(unresolvedKey).isNotNull();
        assertThat(unresolvedKey.path("key").path("deserialized").asBoolean()).isFalse();

        // update and delete the entry through its keyRef although the key class is unknown
        ObjectNode put = JSON.createObjectNode();
        put.put("keyRef", hiddenKeyRef);
        put.put("value", "updated");
        put.put("valueType", "STRING");
        Response updated = httpPostJson("/api/maps/mixed-map/entry", put);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().path("value").path("value").asText()).isEqualTo("updated");
        assertThat(memberMap.get(hiddenKey)).isEqualTo("updated");

        assertThat(httpDelete("/api/maps/mixed-map/entry?keyRef=" + hiddenKeyRef).status()).isEqualTo(204);
        assertThat(memberMap.containsKey(hiddenKey)).isFalse();
        assertThat(httpDelete("/api/maps/mixed-map/entry?keyRef=" + hiddenKeyRef).code()).isEqualTo("ENTRY_NOT_FOUND");
    }

    @Test
    void sqlPredicatePagesThroughMatches() throws Exception {
        IMap<String, HazelcastJsonValue> map = hazelcastClient.getMap("people");
        for (int i = 0; i < 30; i++) {
            map.put("p" + i, new HazelcastJsonValue("{\"age\":" + i + ",\"active\":" + (i % 2 == 0) + "}"));
        }
        // passed as URI template variables so MockMvc encodes them exactly once
        String predicate = "age >= 10 AND active = true";

        int total = 0;
        int page = 0;
        boolean hasNext;
        do {
            JsonNode body = httpGet("/api/maps/people/entries?size=4&page={page}&sqlPredicate={predicate}",
                    page, predicate).body();
            assertThat(body.path("mode").asText()).isEqualTo("PREDICATE");
            for (JsonNode entry : body.path("entries")) {
                assertThat(entry.path("value").path("kind").asText()).isEqualTo("JSON");
            }
            total += body.path("entries").size();
            hasNext = body.path("hasNext").asBoolean();
            page++;
        } while (hasNext && page < 20);
        assertThat(total).isEqualTo(10);

        Response invalid = httpGet("/api/maps/people/entries?sqlPredicate={predicate}", "age >>> 3");
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.code()).isEqualTo("INVALID_PREDICATE");
    }

    @Test
    void keyLookupPutWithTtlAndDelete() throws Exception {
        IMap<Long, String> map = hazelcastClient.getMap("crud-map");
        map.put(1L, "one");

        JsonNode found = httpGet("/api/maps/crud-map/entries?searchKey=1&keyType=LONG").body();
        assertThat(found.path("mode").asText()).isEqualTo("KEY");
        assertThat(found.path("entries").size()).isEqualTo(1);
        assertThat(found.path("entries").get(0).path("key").path("dataType").asText()).isEqualTo("LONG");
        assertThat(httpGet("/api/maps/crud-map/entries?searchKey=999&keyType=LONG").body().path("entries").size())
                .isZero();

        ObjectNode put = JSON.createObjectNode();
        put.put("key", "2");
        put.put("keyType", "LONG");
        put.set("value", JSON.readTree("{\"name\":\"two\",\"tags\":[1,2]}"));
        put.put("valueType", "JSON");
        put.put("ttlSeconds", 3600);
        Response created = httpPostJson("/api/maps/crud-map/entry", put);
        assertThat(created.status()).isEqualTo(200);
        assertThat(created.body().path("value").path("kind").asText()).isEqualTo("JSON");
        assertThat(created.body().path("value").path("value").path("name").asText()).isEqualTo("two");
        assertThat(created.body().path("metadata").path("ttl").asLong()).isEqualTo(3_600_000L);
        assertThat(created.body().path("metadata").path("expirationTime").asLong()).isPositive();
        assertThat(((IMap<Object, Object>) (IMap<?, ?>) map).get(2L)).isInstanceOf(HazelcastJsonValue.class);

        JsonNode entry = httpGet("/api/maps/crud-map/entry?key=2&keyType=LONG").body();
        assertThat(entry.path("value").path("value").path("tags").get(1).asInt()).isEqualTo(2);
        assertThat(entry.path("metadata").path("version").asLong()).isZero();
        // per-entry statistics are disabled by default: hits are unknown instead of -1
        assertThat(entry.path("metadata").has("hits")).isFalse();

        Response missing = httpGet("/api/maps/crud-map/entry?key=404&keyType=LONG");
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.code()).isEqualTo("ENTRY_NOT_FOUND");

        Response badKey = httpGet("/api/maps/crud-map/entry?key=abc&keyType=LONG");
        assertThat(badKey.status()).isEqualTo(400);
        assertThat(badKey.code()).isEqualTo("VALUE_CONVERSION_FAILED");

        assertThat(httpDelete("/api/maps/crud-map/entry?key=2&keyType=LONG").status()).isEqualTo(204);
        assertThat(map.containsKey(2L)).isFalse();
    }

    @Test
    void putStoresClassValuesThatTheOwningApplicationCanRead() throws Exception {
        hazelcastClient.getMap("class-map").put("seed", "seed");
        ObjectNode put = JSON.createObjectNode();
        put.put("key", "dto");
        put.put("keyType", "STRING");
        put.set("value", JSON.readTree("{\"name\":\"Ann\",\"age\":31,\"tags\":[\"a\"],\"born\":\"1990-05-17\"}"));
        put.put("valueType", "CLASS");
        put.put("valueClassName", "testdto.PlainDto");

        Response created = httpPostJson("/api/maps/class-map/entry", put);
        assertThat(created.status()).isEqualTo(200);
        assertThat(created.body().path("value").path("javaType").asText()).isEqualTo("testdto.PlainDto");

        Object stored = cluster().member().getMap("class-map").get("dto");
        assertThat(field(stored, "name")).isEqualTo("Ann");
        assertThat(field(stored, "age")).isEqualTo(31);
    }

    @Test
    void statsComeFromMemberMetrics() throws Exception {
        IMap<String, String> map = hazelcastClient.getMap("stats-map");
        for (int i = 0; i < 25; i++) {
            map.put("s" + i, "v" + i);
        }
        map.get("s1");
        map.get("s2");

        JsonNode stats = awaitJson("/api/maps/stats-map/stats",
                body -> body.path("ownedEntryCount").asLong() == 25 && body.path("getOperationCount").asLong() >= 2);
        assertThat(stats.path("putOperationCount").asLong()).isGreaterThanOrEqualTo(25);
        assertThat(stats.path("ownedEntryMemoryCost").asLong()).isPositive();
        assertThat(stats.path("ownedEntryMemoryCostFormatted").asText()).isNotEmpty();
        assertThat(stats.path("heapCost").asLong()).isPositive();
        assertThat(stats.path("backupCount").asLong()).isEqualTo(1);
        assertThat(stats.path("creationTime").asLong()).isPositive();
        assertThat(stats.path("members").size()).isEqualTo(1);
        assertThat(stats.path("stats").path("available").asBoolean()).isTrue();
    }

    @Test
    void evictAndClearRequireConfirmation() throws Exception {
        IMap<String, String> map = hazelcastClient.getMap("clear-map");
        for (int i = 0; i < 10; i++) {
            map.put("c" + i, "v");
        }
        assertThat(httpPost("/api/maps/clear-map/evict").status()).isEqualTo(204);

        map.put("again", "v");
        Response unconfirmed = httpDelete("/api/maps/clear-map/clear");
        assertThat(unconfirmed.status()).isEqualTo(400);
        assertThat(unconfirmed.code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(map.size()).isEqualTo(1);

        assertThat(httpDelete("/api/maps/clear-map/clear?confirm=clear-map").status()).isEqualTo(204);
        assertThat(map.size()).isZero();
    }

    @Test
    void unknownMapsAreNotCreated() throws Exception {
        Response response = httpGet("/api/maps/does-not-exist/entries");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo("MAP_NOT_FOUND");
        assertThat(hazelcastClient.getDistributedObjects())
                .noneMatch(object -> object.getName().equals("does-not-exist"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        hazelcastClient.getMap("validation-map").put("a", "b");
        assertThat(httpGet("/api/maps/validation-map/entries?cursor=garbage").code()).isEqualTo("INVALID_CURSOR");
        assertThat(httpGet("/api/maps/validation-map/entries?size=0").code()).isEqualTo("VALIDATION_FAILED");
        assertThat(httpGet("/api/maps/validation-map/entries?keyType=NOPE&searchKey=a").code())
                .isEqualTo("VALIDATION_FAILED");
        assertThat(httpPostJson("/api/maps/validation-map/entry", "{not json").code()).isEqualTo("INVALID_JSON");
        assertThat(httpPostJson("/api/maps/validation-map/entry", "{\"value\":\"x\",\"valueType\":\"STRING\"}").code())
                .isEqualTo("VALUE_REQUIRED");
        assertThat(httpGet("/api/maps/validation-map/entry?keyRef=@@").code()).isEqualTo("INVALID_KEY_REF");

        Response unknownEnum = httpPostJson("/api/maps/validation-map/entry",
                "{\"key\":\"a\",\"keyType\":\"STRING\",\"value\":\"x\",\"valueType\":\"NOPE\"}");
        assertThat(unknownEnum.status()).isEqualTo(400);
        assertThat(unknownEnum.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(unknownEnum.body().path("details").path("fields").has("valueType")).isTrue();
    }

    @Test
    void frameworkClientErrorsKeepTheirStatus() throws Exception {
        hazelcastClient.getMap("media-map").put("a", "b");
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.post("/api/maps/media-map/entry")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic(ADMIN_USER, ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(415);
        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("status").asInt()).isEqualTo(415);
    }

    private static JsonNode entryByKeyPreview(JsonNode page, String preview) {
        for (JsonNode entry : page.path("entries")) {
            if (preview.equals(entry.path("key").path("preview").asText())) {
                return entry;
            }
        }
        throw new AssertionError("No entry with key " + preview);
    }
}

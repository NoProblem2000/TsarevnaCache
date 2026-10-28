package io.github.noproblem2000.tsarevnacache.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.noproblem2000.tsarevnacache.metrics.ClusterMetricsService;
import java.time.Duration;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base class for REST API tests (HTTP Basic authenticated MockMvc calls).
 */
@AutoConfigureMockMvc
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ClusterMetricsService metricsService;

    protected Response httpGet(String url, Object... params) throws Exception {
        return execute(MockMvcRequestBuilders.get(url, params));
    }

    protected Response httpPost(String url, Object... params) throws Exception {
        return execute(MockMvcRequestBuilders.post(url, params));
    }

    protected Response httpPostJson(String url, Object body, Object... params) throws Exception {
        return execute(MockMvcRequestBuilders.post(url, params)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : JSON.writeValueAsString(body)));
    }

    protected Response httpDelete(String url, Object... params) throws Exception {
        return execute(MockMvcRequestBuilders.delete(url, params));
    }

    /**
     * Polls a GET endpoint (with fresh metrics) until the condition holds; metrics are sampled by
     * the members once per second in tests.
     */
    protected JsonNode awaitJson(String url, Predicate<JsonNode> condition, Object... params) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            metricsService.invalidate();
            Response response = httpGet(url, params);
            last = response.body();
            if (response.status() == 200 && condition.test(last)) {
                return last;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Condition not met for " + url + ", last response: " + last);
    }

    private Response execute(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.with(httpBasic(ADMIN_USER, ADMIN_PASSWORD))).andReturn();
        String content = result.getResponse().getContentAsString();
        JsonNode body = content.isEmpty() ? null : JSON.readTree(content);
        return new Response(result.getResponse().getStatus(), body);
    }

    /** Status and parsed JSON body. */
    protected record Response(int status, JsonNode body) {

        public String code() {
            return body == null ? null : body.path("code").asText(null);
        }
    }
}

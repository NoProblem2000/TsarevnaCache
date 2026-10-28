package io.github.noproblem2000.tsarevnacache.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noproblem2000.tsarevnacache.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class SpaWebConfigTest extends AbstractApiTest {

    @Test
    void clientRoutesFallBackToIndexWithoutAuthentication() throws Exception {
        // "/" is forwarded to index.html by Spring Boot's welcome page mapping (MockMvc does not follow forwards)
        MockHttpServletResponse root = mvc.perform(MockMvcRequestBuilders.get("/")).andReturn().getResponse();
        assertThat(root.getStatus()).isEqualTo(200);
        assertThat(root.getForwardedUrl()).isEqualTo("index.html");

        for (String path : new String[] {"/maps/orders", "/structures/queue/jobs", "/favicon.svg.missing"}) {
            MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse();
            assertThat(response.getStatus()).as(path).isEqualTo(200);
            assertThat(response.getContentAsString()).as(path).contains("spa-test-shell");
        }
    }

    @Test
    void unknownApiPathsStayJsonErrors() throws Exception {
        Response response = httpGet("/api/does-not-exist");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo("NOT_FOUND");
    }
}

package io.github.noproblem2000.tsarevnacache.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.noproblem2000.tsarevnacache.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthSecurityTest extends AbstractIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mvc;

    @Test
    void apiRequiresAuthenticationWithoutBrowserPopup() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void httpBasicIsAcceptedForTools() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(ADMIN_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Cookie csrf = fetchCsrfCookie();
        mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void spaSessionLoginAndLogout() throws Exception {
        Cookie csrf = fetchCsrfCookie();

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header(CSRF_HEADER, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        // The response first expires the old token cookie, then sets the new one (the browser keeps the last).
        Cookie rotatedCsrf = Arrays.stream(login.getResponse().getCookies())
                .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
                .reduce((first, second) -> second)
                .orElse(null);
        assertThat(session).isNotNull();
        assertThat(rotatedCsrf).isNotNull();
        assertThat(rotatedCsrf.getValue()).isNotBlank().isNotEqualTo(csrf.getValue());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER));

        mvc.perform(post("/api/auth/logout").session(session)
                        .cookie(rotatedCsrf).header(CSRF_HEADER, rotatedCsrf.getValue()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static String credentials(String password) {
        return "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + password + "\"}";
    }
}

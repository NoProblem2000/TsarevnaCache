package io.github.noproblem2000.tsarevnacache.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React single-page application bundled in {@code classpath:/static}.
 *
 * <p>Fingerprinted assets ({@code /assets/**}) are cached for a year; every other path that does
 * not match a file falls back to {@code index.html} so client side routes such as
 * {@code /maps/orders} survive a page reload. {@code /api/**} never falls back and keeps returning
 * JSON errors.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Resolves existing files, otherwise {@code index.html} for non-API paths. */
    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.equals("api")) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }
}

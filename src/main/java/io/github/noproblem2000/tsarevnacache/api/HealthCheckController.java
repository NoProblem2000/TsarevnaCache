package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.healthcheck.HealthCheckEngineService;
import io.github.noproblem2000.tsarevnacache.healthcheck.HealthCheckIgnoreService;
import io.github.noproblem2000.tsarevnacache.healthcheck.HealthCheckReport;
import io.github.noproblem2000.tsarevnacache.healthcheck.PrometheusExporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automated healthcheck: latest report, manual run, ignore list and a Prometheus exporter.
 */
@RestController
@RequestMapping("/api/healthcheck")
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthCheckEngineService engine;
    private final HealthCheckIgnoreService ignores;

    /** Latest report (the first call runs the checks). */
    @GetMapping("/report")
    public HealthCheckReport report() {
        return engine.report();
    }

    /** Runs all checks now. */
    @PostMapping("/run")
    public HealthCheckReport run() {
        return engine.runChecks();
    }

    /**
     * Hides an issue ({@code MAP_UNBOUNDED_NO_TTL:countries}) or a whole rule
     * ({@code MAP_UNBOUNDED_NO_TTL:*}); returns the report with the list applied.
     */
    @PostMapping("/ignore")
    public HealthCheckReport ignore(@Valid @RequestBody IgnoreRequest request, Principal principal) {
        ignores.add(request.issueId(), request.reason(), principal == null ? null : principal.getName());
        return engine.report();
    }

    /**
     * Removes an entry of the ignore list. The id may contain slashes (map names); ids with
     * characters the HTTP firewall rejects in paths ({@code %}, {@code ;}) use the query variant.
     */
    @DeleteMapping("/ignore/{*issueId}")
    public HealthCheckReport unignore(@PathVariable String issueId) {
        ignores.remove(issueId.startsWith("/") ? issueId.substring(1) : issueId);
        return engine.report();
    }

    /** Same as {@link #unignore(String)} with the id as query parameter {@code issueId}. */
    @DeleteMapping(value = "/ignore", params = "issueId")
    public HealthCheckReport unignoreByParameter(@RequestParam String issueId) {
        ignores.remove(issueId);
        return engine.report();
    }

    /** Health score, issue counts and map sizes in the Prometheus text format. */
    @GetMapping(value = "/prometheus", produces = "text/plain")
    public ResponseEntity<String> prometheus() {
        HealthCheckReport report = engine.report();
        String body = PrometheusExporter.format(report,
                engine.latestRun().map(run -> run.snapshot().maps()).orElse(List.of()));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, PrometheusExporter.CONTENT_TYPE).body(body);
    }

    /**
     * @param issueId exact issue id or {@code CHECK_TYPE:*} for the whole rule
     * @param reason  why the finding does not apply
     */
    public record IgnoreRequest(@NotBlank @Size(max = 1024) String issueId, @Size(max = 2000) String reason) {
    }
}

package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.logs.LiveEventBuffer;
import io.github.noproblem2000.tsarevnacache.logs.LiveEventsDTO;
import io.github.noproblem2000.tsarevnacache.logs.LogFileDTO;
import io.github.noproblem2000.tsarevnacache.logs.LogFilesDTO;
import io.github.noproblem2000.tsarevnacache.logs.LogLevelFilter;
import io.github.noproblem2000.tsarevnacache.logs.LogTailDTO;
import io.github.noproblem2000.tsarevnacache.logs.LogViewerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Log viewer: server side log files ({@code admin.logs.paths}) and the live stream of cluster and
 * log events.
 */
@Validated
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogViewerService logViewer;
    private final LiveEventBuffer liveEvents;

    /** The log files that can be viewed and downloaded. */
    @GetMapping("/files")
    public LogFilesDTO files() {
        return logViewer.files();
    }

    /**
     * The last {@code lines} matching lines of a file, read backwards from its end. Without
     * {@code file} the first existing file (normally the admin service's own log) is read.
     */
    @GetMapping("/tail")
    public LogTailDTO tail(@RequestParam(required = false) String file,
                           @RequestParam(required = false) @Min(1) Integer lines,
                           @RequestParam(defaultValue = "ALL") LogLevelFilter level,
                           @RequestParam(required = false) String search) {
        return logViewer.tail(file, lines, level, search);
    }

    /** The whole file as an attachment (streamed, never loaded into memory). */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam(required = false) String file) {
        LogFileDTO log = logViewer.requireFile(file);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(log.name(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(logViewer.open(log)));
    }

    /** Live stream events newer than {@code after} (all buffered events for {@code after=0}). */
    @GetMapping("/live")
    public LiveEventsDTO live(@RequestParam(defaultValue = "0") @Min(0) long after,
                              @RequestParam(defaultValue = "ALL") LogLevelFilter level,
                              @RequestParam(required = false) String search,
                              @RequestParam(defaultValue = "1000") @Min(1) @Max(100_000) int limit) {
        return liveEvents.events(after, level, search, limit);
    }

    /** Empties the live stream buffer. */
    @DeleteMapping("/live")
    public ResponseEntity<Void> clearLive() {
        liveEvents.clear();
        return ResponseEntity.noContent().build();
    }
}

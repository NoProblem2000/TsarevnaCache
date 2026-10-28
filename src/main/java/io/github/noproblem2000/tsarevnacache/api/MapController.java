package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.api.dto.DataType;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.EntryPageDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.MapStatsDTO;
import io.github.noproblem2000.tsarevnacache.api.dto.PutEntryRequest;
import io.github.noproblem2000.tsarevnacache.maps.EntryQuery;
import io.github.noproblem2000.tsarevnacache.maps.KeySpec;
import io.github.noproblem2000.tsarevnacache.maps.MapInspectorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IMap inspector and CRUD.
 *
 * <p>Single entries are addressed either by {@code keyRef} (opaque serialized key returned by the
 * list/get endpoints) or by {@code key} + {@code keyType} (+ {@code keyClassName} for CLASS keys).
 */
@Validated
@RestController
@RequestMapping("/api/maps/{mapName}")
@RequiredArgsConstructor
public class MapController {

    private final MapInspectorService mapInspector;

    /** {@code LocalMapStats} aggregated over all members. */
    @GetMapping("/stats")
    public MapStatsDTO stats(@PathVariable String mapName) {
        return mapInspector.stats(mapName);
    }

    /**
     * One page of entries: exact key lookup ({@code searchKey}/{@code keyRef}), SQL predicate
     * paging ({@code sqlPredicate} + {@code page}) or cursor based scan ({@code cursor}).
     */
    @GetMapping("/entries")
    public EntryPageDTO entries(@PathVariable String mapName,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(required = false) String searchKey,
                                @RequestParam(required = false) DataType keyType,
                                @RequestParam(required = false) String keyClassName,
                                @RequestParam(required = false) String keyRef,
                                @RequestParam(required = false) String sqlPredicate) {
        return mapInspector.entries(mapName,
                new EntryQuery(page, size, cursor, searchKey, keyType, keyClassName, keyRef, sqlPredicate));
    }

    /** Value and {@code EntryView} metadata of one entry. */
    @GetMapping("/entry")
    public EntryDTO entry(@PathVariable String mapName,
                          @RequestParam(required = false) String key,
                          @RequestParam(required = false) DataType keyType,
                          @RequestParam(required = false) String keyClassName,
                          @RequestParam(required = false) String keyRef) {
        return mapInspector.entry(mapName, new KeySpec(keyRef, key, keyType, keyClassName));
    }

    /** Creates or updates an entry; returns the stored entry. */
    @PostMapping("/entry")
    public EntryDTO put(@PathVariable String mapName, @Valid @RequestBody PutEntryRequest request) {
        return mapInspector.put(mapName, request);
    }

    @DeleteMapping("/entry")
    public ResponseEntity<Void> delete(@PathVariable String mapName,
                                       @RequestParam(required = false) String key,
                                       @RequestParam(required = false) DataType keyType,
                                       @RequestParam(required = false) String keyClassName,
                                       @RequestParam(required = false) String keyRef) {
        mapInspector.delete(mapName, new KeySpec(keyRef, key, keyType, keyClassName));
        return ResponseEntity.noContent().build();
    }

    /** {@code IMap.evictAll()}. */
    @PostMapping("/evict")
    public ResponseEntity<Void> evictAll(@PathVariable String mapName) {
        mapInspector.evictAll(mapName);
        return ResponseEntity.noContent().build();
    }

    /** {@code IMap.clear()}; {@code confirm} must repeat the map name. */
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clear(@PathVariable String mapName,
                                      @RequestParam(required = false) String confirm) {
        mapInspector.clear(mapName, confirm);
        return ResponseEntity.noContent().build();
    }
}

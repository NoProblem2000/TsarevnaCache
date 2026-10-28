package io.github.noproblem2000.tsarevnacache.api;

import io.github.noproblem2000.tsarevnacache.api.dto.OtherStructureDTO;
import io.github.noproblem2000.tsarevnacache.structures.OtherStructuresService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic view and maintenance of {@code queue}, {@code set}, {@code list}, {@code multimap} and
 * {@code replicatedmap} structures.
 */
@Validated
@RestController
@RequestMapping("/api/other/{type}/{name}")
@RequiredArgsConstructor
public class OtherStructuresController {

    private final OtherStructuresService service;

    /** Size and the first {@code limit} items. */
    @GetMapping
    public OtherStructureDTO view(@PathVariable String type, @PathVariable String name,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return service.view(service.parseType(type), name, limit);
    }

    /** Removes one item identified by {@code ref} and/or {@code keyRef}. */
    @DeleteMapping("/item")
    public ResponseEntity<Void> removeItem(@PathVariable String type, @PathVariable String name,
                                           @RequestParam(required = false) String ref,
                                           @RequestParam(required = false) String keyRef) {
        service.removeItem(service.parseType(type), name, ref, keyRef);
        return ResponseEntity.noContent().build();
    }

    /** Clears the structure; {@code confirm} must repeat its name. */
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clear(@PathVariable String type, @PathVariable String name,
                                      @RequestParam(required = false) String confirm) {
        service.clear(service.parseType(type), name, confirm);
        return ResponseEntity.noContent().build();
    }
}

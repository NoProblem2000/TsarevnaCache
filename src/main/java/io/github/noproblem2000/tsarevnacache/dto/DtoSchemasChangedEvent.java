package io.github.noproblem2000.tsarevnacache.dto;

/**
 * Published after the DTO schema registry changed through the API (schema saved or deleted,
 * factory parsed, file imported): values of the affected classes may have become readable or
 * unreadable, so derived state such as the healthcheck report is refreshed.
 *
 * @param reason short description, e.g. {@code "schema 1:2 saved"}
 */
public record DtoSchemasChangedEvent(String reason) {
}

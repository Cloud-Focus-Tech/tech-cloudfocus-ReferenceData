package tech.cloudfocus.ReferenceData.controller;

import java.util.Map;
import java.util.List;
import tech.cloudfocus.ReferenceData.model.Collection;
import tech.cloudfocus.ReferenceData.model.ItemExistence;
import org.springframework.web.bind.annotation.*;
import tech.cloudfocus.ReferenceData.service.CollectionService;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

// ENDPOINT:  /health/live
// ENDPOINT:  /health/ready
// ENDPOINT:  /collections
// ENDPOINT:  /collection/{id}
// ENDPOINT:  /collection/{id}/items
// ENDPOINT:  /collection/{id}/item/{itemId}
// ENDPOINT:  /collection/{id}/item/{itemId}/exists

@RestController
@RequestMapping("/api")
public class collections {

    private final CollectionService collectionService;

    public collections(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @GetMapping("/health/live")
    public Map<String, String> liveEndpoint() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health/ready")
    public Map<String, String> readyEndpoint() {
        if (!collectionService.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Collections have not loaded");
        }
        return Map.of("status", "UP");
    }

    /// Get all collections
    /// @return List of collections
    /// Get all collections endpoint
    /// @response 200 List of collections
    /// @response 500 Internal Server Error
    /// @throws Exception if an error occurs while fetching collections
    /// @example /collections
    /// @description Retrieves all collections available in the system
    /// @tags Collections
    /// @summary Get all collections
    /// @operationId getCollections
    /// @method GET
    /// @path /collections
    /// @security none
    @GetMapping("/collections")
    public List<Collection> getCollections() {
        return collectionService.getCollections();
    }

    @GetMapping("/collection/{id}")
    public Collection getCollectionById(@PathVariable String id) {
        return collectionService.getCollection(id);
    }

    @GetMapping("/collection/{id}/items")
    public JsonNode getCollectionItems(@PathVariable String id) {
        return collectionService.getItems(id);
    }

    @Operation(summary = "Get a single item")
    @GetMapping("/collection/{id}/item/{itemId}")
    public JsonNode getCollectionItem(@PathVariable String id, @PathVariable String itemId) {
        return collectionService.getItem(id, itemId);
    }

    @Operation(summary = "Check whether an item exists")
    @GetMapping("/collection/{id}/item/{itemId}/exists")
    public ItemExistence checkCollectionItem(@PathVariable String id, @PathVariable String itemId) {
        return collectionService.itemExists(id, itemId);
    }

    @Operation(summary = "Get a single item by query parameter",
            description = "Accepts the complete item ID, including slashes in timezone names.")
    @GetMapping("/collection/{id}/item")
    public JsonNode getCollectionItemByQuery(@PathVariable String id,
            @Parameter(example = "Africa/Abidjan") @RequestParam String itemId) {
        return collectionService.getItem(id, requireItemId(itemId));
    }

    @Operation(summary = "Check whether an item exists",
            description = "Accepts the complete item ID as a query parameter, including slashes in timezone names.")
    @GetMapping("/collection/{id}/item/exists")
    public ItemExistence checkCollectionItemByQuery(@PathVariable String id,
            @Parameter(example = "Africa/Abidjan") @RequestParam String itemId) {
        return collectionService.itemExists(id, requireItemId(itemId));
    }

    // Keep literal slash-separated IDs working without exposing a wildcard as an OpenAPI parameter.
    @Hidden
    @GetMapping("/collection/{id}/item/{*itemId}")
    public Object getCollectionItemPath(@PathVariable String id, @PathVariable String itemId) {
        // Spring's catch-all variable includes the separator before the captured ID.
        String completeId = itemId.startsWith("/") ? itemId.substring(1) : itemId;
        // Catch-all variables must end the mapping, so handle the existence suffix here.
        if (completeId.endsWith("/exists")) {
            return collectionService.itemExists(id,
                    requireItemId(completeId.substring(0, completeId.length() - "/exists".length())));
        }
        return collectionService.getItem(id, requireItemId(completeId));
    }

    @Hidden
    @GetMapping("/collection/{id}/Item/{*itemId}")
    public ItemExistence checkCollectionItemCapitalizedPath(@PathVariable String id, @PathVariable String itemId) {
        if (!itemId.endsWith("/exists")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String completeId = itemId.startsWith("/") ? itemId.substring(1) : itemId;
        return collectionService.itemExists(id,
                requireItemId(completeId.substring(0, completeId.length() - "/exists".length())));
    }

    private static String requireItemId(String itemId) {
        if (itemId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId must not be empty");
        }
        return itemId;
    }


}

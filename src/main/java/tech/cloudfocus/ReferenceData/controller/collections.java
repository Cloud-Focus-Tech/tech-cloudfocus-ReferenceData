package tech.cloudfocus.ReferenceData.controller;

import java.util.Map;
import java.util.List;
import tech.cloudfocus.ReferenceData.model.Collection;
import org.springframework.web.bind.annotation.*;
import tech.cloudfocus.ReferenceData.service.CollectionService;

// ENDPOINT:  /health/live
// ENDPOINT:  /health/ready
// ENDPOINT:  /collections
// ENDPOINT:  /collection/{id}
// ENDPOINT:  /collection/{id}/items

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
    public Map<String, String> getCollectionById(@PathVariable String id) {
        return Map.of("message", "Details of collection " + id);
    }

    @GetMapping("/collection/{id}/items")
    public Map<String, String> getCollectionItems(@PathVariable String id) {
        return Map.of("message", "Items of collection " + id);
    }
}

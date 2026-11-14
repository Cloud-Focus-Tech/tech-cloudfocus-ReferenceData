package tech.cloudfocus.ReferenceData.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import tech.cloudfocus.ReferenceData.model.Collection;
import tech.cloudfocus.ReferenceData.repository.CollectionRepository;

@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    public List<Collection> getCollections() {
        return collectionRepository.findAll();
    }

    public Collection getCollection(String id) {
        return collectionRepository.findByName(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
    }

    public JsonNode getItems(String id) {
        return collectionRepository.findItems(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found"));
    }

    public boolean isReady() { return collectionRepository.isLoaded(); }
}

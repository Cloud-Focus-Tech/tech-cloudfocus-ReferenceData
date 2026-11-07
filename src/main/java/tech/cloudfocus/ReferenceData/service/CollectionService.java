package tech.cloudfocus.ReferenceData.service;

import java.util.List;

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
}

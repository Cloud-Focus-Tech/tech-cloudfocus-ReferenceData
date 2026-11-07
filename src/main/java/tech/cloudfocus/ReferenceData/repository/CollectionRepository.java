package tech.cloudfocus.ReferenceData.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import tech.cloudfocus.ReferenceData.model.Collection;

@Repository
public class CollectionRepository {

    public List<Collection> findAll() {
        return List.of(
                new Collection("currencies", "set", "v1", 3),
                new Collection("languages", "keyValue", "v1", 7927),
                new Collection("timezones", "keyValue", "v1", 597),
                new Collection("countries", "keyValue", "v1", 249));
    }
}

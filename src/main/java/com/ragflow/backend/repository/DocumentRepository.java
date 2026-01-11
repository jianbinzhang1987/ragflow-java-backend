package com.ragflow.backend.repository;

import com.ragflow.backend.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByCollection(String collection);

    DocumentEntity findFirstByCollectionAndName(String collection, String name);

    org.springframework.data.domain.Page<DocumentEntity> findByCollection(String collection,
            org.springframework.data.domain.Pageable pageable);
}

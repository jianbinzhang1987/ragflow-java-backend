package com.ragflow.backend.repository;

import com.ragflow.backend.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<ChunkEntity, Long> {
    List<ChunkEntity> findByDocId(Long docId);
}

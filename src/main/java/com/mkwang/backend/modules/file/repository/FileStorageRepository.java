package com.mkwang.backend.modules.file.repository;

import com.mkwang.backend.modules.file.entity.FileStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileStorageRepository extends JpaRepository<FileStorage, Long> {
    Optional<FileStorage> findByCloudinaryPublicId(String cloudinaryPublicId);
    List<FileStorage> findAllByCloudinaryPublicIdIn(List<String> publicIds);
}

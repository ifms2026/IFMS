package com.mkwang.backend.modules.file.mapper;

import com.mkwang.backend.modules.file.dto.request.FileStorageRequest;
import com.mkwang.backend.modules.file.entity.FileStorage;
import org.springframework.stereotype.Service;

@Service
public class FileStorageMapper {

    public FileStorage toFileStorage (FileStorageRequest request) {
        return FileStorage.builder()
                .fileName(request.getFileName())
                .cloudinaryPublicId(request.getCloudinaryPublicId())
                .url(request.getUrl())
                .fileType(request.getFileType())
                .size(request.getSize())
                .build();
    }
}

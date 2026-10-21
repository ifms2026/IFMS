package com.mkwang.backend.modules.file.service;

import com.mkwang.backend.common.exception.ResourceNotFoundException;
import com.mkwang.backend.modules.file.dto.request.FileStorageRequest;
import com.mkwang.backend.modules.file.entity.FileStorage;
import com.mkwang.backend.modules.file.mapper.FileStorageMapper;
import com.mkwang.backend.modules.file.repository.FileStorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageRepository fileStorageRepository;
    private final FileStorageMapper fileStorageMapper;
    private final CloudinaryService cloudinaryService;

    public FileStorage save(FileStorageRequest request) {
        var existing = fileStorageRepository.findByCloudinaryPublicId(request.getCloudinaryPublicId());
        if (existing.isPresent()) {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof com.mkwang.backend.modules.auth.security.UserDetailsAdapter adapter)
                    || !adapter.getUser().getId().equals(existing.get().getCreatedBy())) {
                throw new com.mkwang.backend.common.exception.UnauthorizedException("Không sở hữu chứng từ này");
            }
            return existing.get();
        }
        return fileStorageRepository.save(fileStorageMapper.toFileStorage(request));
    }

    public List<FileStorage> saveAll(List<FileStorageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<FileStorage> files = requests.stream()
                .map(this::save)
                .toList();
        return fileStorageRepository.saveAll(files);
    }

    public FileStorage getFile(Long id) {
        return fileStorageRepository.findById(id)
                .orElseThrow( () -> new ResourceNotFoundException("File not found with id: " + id));
    }

    public FileStorage getOwned(Long id, Long ownerId) {
        FileStorage file = getFile(id);
        if (!ownerId.equals(file.getCreatedBy())) throw new com.mkwang.backend.common.exception.UnauthorizedException("Chứng từ không thuộc bạn");
        return file;
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public com.mkwang.backend.modules.file.dto.response.RegisteredDocumentResponse register(
            com.mkwang.backend.modules.file.dto.request.RegisterDocumentRequest request) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication.getPrincipal() instanceof com.mkwang.backend.modules.auth.security.UserDetailsAdapter adapter)) {
            throw new com.mkwang.backend.common.exception.UnauthorizedException("Cần đăng nhập");
        }
        cloudinaryService.verifyDocumentRegistration(request, adapter.getUser().getId());
        FileStorage file = save(request);
        return new com.mkwang.backend.modules.file.dto.response.RegisteredDocumentResponse(file.getId(),file.getFileName(),file.getUrl());
    }

    public List<FileStorage> getMutipleFiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return fileStorageRepository.findAllById(ids);
    }


    @Transactional
    public void deleteFile(Long id) {
        FileStorage file = fileStorageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        cloudinaryService.deleteFile(file.getCloudinaryPublicId());
        fileStorageRepository.delete(file);
    }

    @Transactional
    public boolean deleteFileBestEffort(Long id) {
        FileStorage file = fileStorageRepository.findById(id).orElse(null);
        if (file == null) {
            return false;
        }

        try {
            cloudinaryService.deleteFile(file.getCloudinaryPublicId());
            fileStorageRepository.delete(file);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "Could not delete old file from Cloudinary. Keeping metadata for later cleanup. fileId={}, publicId={}",
                    file.getId(),
                    file.getCloudinaryPublicId(),
                    ex
            );
            return false;
        }
    }
}

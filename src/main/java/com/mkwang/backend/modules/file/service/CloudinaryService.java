package com.mkwang.backend.modules.file.service;

import com.cloudinary.Cloudinary;
import com.mkwang.backend.common.exception.BadRequestException;
import com.mkwang.backend.common.exception.InternalSystemException;
import com.mkwang.backend.config.CloudinaryConfig;
import com.mkwang.backend.modules.file.dto.response.UploadSignatureResponse;
import com.mkwang.backend.modules.file.entity.UploadFolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final CloudinaryConfig cloudinaryConfig;

    public UploadSignatureResponse generateSignature(UploadFolder folder) {
        long timestamp = Instant.now().getEpochSecond();

        Map<String, Object> params = new TreeMap<>();
        params.put("folder", folder.getPath());
        params.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(params, cloudinaryConfig.getApiSecret());

        return UploadSignatureResponse.builder()
                .signature(signature)
                .timestamp(timestamp)
                .apiKey(cloudinaryConfig.getApiKey())
                .cloudName(cloudinaryConfig.getCloudName())
                .folder(folder.getPath())
                .build();
    }

    public void deleteFile(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BadRequestException("Cloudinary publicId is required");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, Map.of());
            String deleteResult = String.valueOf(result.get("result"));

            // Cloudinary returns "ok" when deleted and "not found" when already absent.
            if (!"ok".equalsIgnoreCase(deleteResult) && !"not found".equalsIgnoreCase(deleteResult)) {
                throw new InternalSystemException("Failed to delete file from Cloudinary");
            }
        } catch (InternalSystemException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InternalSystemException("Failed to delete file from Cloudinary");
        }
    }
}
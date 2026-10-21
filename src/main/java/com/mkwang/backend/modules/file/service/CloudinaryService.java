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
        String path = folder.getPath();
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (folder == UploadFolder.REQUEST
                && authentication != null
                && authentication.getPrincipal() instanceof com.mkwang.backend.modules.auth.security.UserDetailsAdapter adapter) {
            path += "/" + adapter.getUser().getId();
        }
        params.put("folder", path);
        params.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(params, cloudinaryConfig.getApiSecret());

        return UploadSignatureResponse.builder()
                .signature(signature)
                .timestamp(timestamp)
                .apiKey(cloudinaryConfig.getApiKey())
                .cloudName(cloudinaryConfig.getCloudName())
                .folder(path)
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

    public void verifyDocumentRegistration(com.mkwang.backend.modules.file.dto.request.RegisterDocumentRequest request, Long actorId) {
        String prefix = UploadFolder.REQUEST.getPath() + "/" + actorId + "/";
        if (!request.getCloudinaryPublicId().startsWith(prefix)) throw new BadRequestException("Chứng từ không thuộc thư mục upload của bạn");
        String signed = cloudinary.apiSignRequest(Map.of("public_id", request.getCloudinaryPublicId(),
                "version", request.getUploadVersion()), cloudinaryConfig.getApiSecret());
        if (!java.security.MessageDigest.isEqual(signed.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                request.getUploadSignature().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BadRequestException("Chữ ký upload chứng từ không hợp lệ");
        }
        String decoded;
        try { decoded=java.net.URLDecoder.decode(request.getUrl(),java.nio.charset.StandardCharsets.UTF_8); }
        catch(IllegalArgumentException e){throw new BadRequestException("URL chứng từ không hợp lệ");}
        String root="https://res.cloudinary.com/"+cloudinaryConfig.getCloudName();
        String asset="/v"+request.getUploadVersion()+"/"+request.getCloudinaryPublicId();
        String pattern=java.util.regex.Pattern.quote(root)+"/(image|raw)/upload"+java.util.regex.Pattern.quote(asset)+"(\\.[A-Za-z0-9]+)?";
        if (!decoded.matches(pattern)) {
            throw new BadRequestException("URL chứng từ không thuộc kho lưu trữ được cấu hình");
        }
    }
}

package com.mkwang.backend.modules.file.controller;

import com.mkwang.backend.common.dto.ApiResponse;
import com.mkwang.backend.modules.file.dto.response.UploadSignatureResponse;
import com.mkwang.backend.modules.file.entity.UploadFolder;
import com.mkwang.backend.modules.file.service.CloudinaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class FileStorageController {

    private final CloudinaryService cloudinaryService;

    @GetMapping("/signature")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<UploadSignatureResponse>> getSignature(
            @RequestParam UploadFolder folder
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(cloudinaryService.generateSignature(folder)));
    }

}

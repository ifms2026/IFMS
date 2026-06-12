package com.mkwang.backend.modules.file.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UploadFolder {
    AVATAR("avatars"),
    REQUEST("requests");

    private final String path;
}
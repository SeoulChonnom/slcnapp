package com.seoulchonnom.slcnapp.depot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFile {
    private byte[] image;
    private String mimeType;
}
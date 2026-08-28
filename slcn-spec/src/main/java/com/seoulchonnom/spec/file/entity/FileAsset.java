package com.seoulchonnom.spec.file.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.seoulchonnom.spec.common.entity.DomainEntity;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class FileAsset extends DomainEntity {
	private FileType type;
	private String originalFilename;
	private String storedFilename;
	private String path;
	private String mimeType;
	private long size;
	private int width;
	private int height;
	/**
	 * 업로드 시점에 실제로 생성된 파생본. 생성에 실패했거나 이 필드가 없는 과거 자산은 비어 있고, 조회 시 원본으로 폴백한다.
	 */
	@Builder.Default
	private List<FileVariant> variants = new ArrayList<>();

	public FileAsset(FileType type, String originalFilename, String storedFilename, String mimeType, long size) {
		super();
		this.type = type;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.path = type.getValue() + "/" + storedFilename;
		this.mimeType = mimeType;
		this.size = size;
		this.variants = new ArrayList<>();
	}

	public FileReference toFileReference() {
		return new FileReference(type, storedFilename);
	}

	public List<FileVariant> getVariants() {
		return variants == null ? List.of() : variants;
	}

	public List<String> variantNames() {
		return getVariants().stream().map(FileVariant::getVariant).toList();
	}

	public Optional<FileVariant> findVariant(ImageVariant variant) {
		if (variant == null) {
			return Optional.empty();
		}

		return getVariants().stream()
			.filter(candidate -> variant.getValue().equals(candidate.getVariant()))
			.findFirst();
	}

	/**
	 * 파생본 저장 파일명. 원본과 같은 디렉터리에 {uuid}_{variant}.{ext}로 둔다.
	 */
	public String variantFilename(ImageVariant variant, String extension) {
		return baseFilename() + "_" + variant.getValue() + "." + extension;
	}

	/**
	 * 사용자가 저장할 때 보게 될 파일명. 원본 파일명을 살리되 파생본은 접미사와 실제 확장자를 붙인다.
	 * 업로드된 이름을 그대로 쓰므로 경로 구분자와 제어 문자는 제거한다.
	 */
	public String downloadFilename(FileVariant fileVariant) {
		String source = sanitizeFilename(originalFilename);
		if (source.isBlank()) {
			source = sanitizeFilename(storedFilename);
		}

		if (fileVariant == null) {
			return source;
		}

		int extensionIndex = source.lastIndexOf('.');
		String base = extensionIndex < 0 ? source : source.substring(0, extensionIndex);
		String variantFile = fileVariant.getFilename();
		int variantExtensionIndex = variantFile.lastIndexOf('.');
		String extension = variantExtensionIndex < 0 ? "" : variantFile.substring(variantExtensionIndex);
		return base + "_" + fileVariant.getVariant() + extension;
	}

	private String sanitizeFilename(String value) {
		if (value == null) {
			return "";
		}

		String name = value.replace('\\', '/');
		int separatorIndex = name.lastIndexOf('/');
		if (separatorIndex >= 0) {
			name = name.substring(separatorIndex + 1);
		}
		return name.replaceAll("[\\p{Cntrl}\"]", "").trim();
	}

	private String baseFilename() {
		int extensionIndex = storedFilename.lastIndexOf('.');
		return extensionIndex < 0 ? storedFilename : storedFilename.substring(0, extensionIndex);
	}
}

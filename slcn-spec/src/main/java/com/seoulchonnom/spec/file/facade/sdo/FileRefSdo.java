package com.seoulchonnom.spec.file.facade.sdo;

import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileRefSdo {
	@NotNull(message = "파일 타입은 필수값 입니다.")
	private FileType type;

	@NotBlank(message = "파일명은 필수값 입니다.")
	@Pattern(
		regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\.(jpg|png|jpeg|gif|svg)$",
		message = "파일명 형식이 올바르지 않습니다.")
	private String filename;

	public static FileRefSdo from(FileReference fileReference) {
		if (fileReference == null) {
			return null;
		}
		return new FileRefSdo(fileReference.getType(), fileReference.getFilename());
	}
}

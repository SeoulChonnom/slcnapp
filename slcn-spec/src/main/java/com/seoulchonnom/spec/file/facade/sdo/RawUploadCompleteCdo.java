package com.seoulchonnom.spec.file.facade.sdo;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RawUploadCompleteCdo {
	/**
	 * 세션 생성 때 받은 값. 서버에 저장된 값과 다르면 거부한다.
	 */
	private String uploadId;
	private List<RawUploadPartCdo> parts = new ArrayList<>();
}

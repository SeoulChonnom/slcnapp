package com.seoulchonnom.aggregate.file.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 테스트용 JPEG에 APP 세그먼트를 끼워 넣는다.
 * JDK JPEG writer는 EXIF도 ICC 프로필도 쓰지 않으므로 카메라·휴대폰 JPEG를 흉내 내려면 직접 넣어야 한다.
 */
final class JpegSegments {
	private JpegSegments() {
	}

	/**
	 * IFD0에 방향 태그(0x0112) 하나만 있는 최소 EXIF APP1.
	 */
	static byte[] withExifOrientation(byte[] jpeg, int orientation) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(body);
		data.write("Exif".getBytes(StandardCharsets.US_ASCII));
		data.writeShort(0);
		// TIFF 헤더: 빅엔디언, 매직 42, IFD0는 헤더 시작에서 8바이트 뒤
		data.writeBytes("MM");
		data.writeShort(42);
		data.writeInt(8);
		data.writeShort(1);
		data.writeShort(0x0112);
		data.writeShort(3);
		data.writeInt(1);
		data.writeShort(orientation);
		data.writeShort(0);
		data.writeInt(0);
		return insertAfterSoi(jpeg, 0xE1, body.toByteArray());
	}

	static byte[] withIccProfile(byte[] jpeg, byte[] profile) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("ICC_PROFILE".getBytes(StandardCharsets.US_ASCII));
		body.write(0);
		// 시퀀스 번호 1 / 전체 1개
		body.write(1);
		body.write(1);
		body.write(profile);
		return insertAfterSoi(jpeg, 0xE2, body.toByteArray());
	}

	private static byte[] insertAfterSoi(byte[] jpeg, int marker, byte[] body) {
		int length = body.length + 2;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(0xFF);
		out.write(marker);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.write(body, 0, body.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}
}

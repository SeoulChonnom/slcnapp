package com.seoulchonnom.aggregate.file.util;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FileExtException;

class ImageInspectorTest {
	private static final byte[] PNG_SIGNATURE = {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

	private final ImageInspector imageInspector = new ImageInspector();

	@TempDir
	Path tempDir;

	@Test
	void readScaled_shouldSubsampleCameraSizedJpegToQuarterWidth() throws Exception {
		// 회색조 단색이라 원본 생성에도 40 MB 정도만 쓴다. 인코딩 결과는 수백 KB다.
		Path jpeg = tempDir.resolve("camera.jpg");
		ImageIO.write(new BufferedImage(7728, 5152, BufferedImage.TYPE_BYTE_GRAY), "jpeg", jpeg.toFile());

		ImageInspector.ScaledImage scaled = imageInspector.readScaled(jpeg, 960);

		assertThat(scaled.image().getWidth()).isEqualTo(1932);
		assertThat(scaled.image().getHeight()).isEqualTo(1288);
		assertThat(scaled.originalWidth()).isEqualTo(7728);
		assertThat(scaled.originalHeight()).isEqualTo(5152);
	}

	@Test
	void readScaled_shouldKeepTallImagesWithinPixelBudget() throws Exception {
		Path png = tempDir.resolve("tall.png");
		ImageIO.write(new BufferedImage(500, 20000, BufferedImage.TYPE_BYTE_GRAY), "png", png.toFile());

		ImageInspector.ScaledImage scaled = imageInspector.readScaled(png, 960);

		long decodedPixels = (long)scaled.image().getWidth() * scaled.image().getHeight();
		assertThat(decodedPixels).isLessThanOrEqualTo(ImageInspector.DECODE_PIXEL_BUDGET);
		assertThat(scaled.originalWidth()).isEqualTo(500);
		assertThat(scaled.originalHeight()).isEqualTo(20000);
	}

	@Test
	void readScaled_shouldNotSubsampleSmallImages() throws Exception {
		Path png = tempDir.resolve("small.png");
		ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", png.toFile());

		ImageInspector.ScaledImage scaled = imageInspector.readScaled(png, 960);

		assertThat(scaled.image().getWidth()).isEqualTo(400);
		assertThat(scaled.image().getHeight()).isEqualTo(300);
	}

	@Test
	void subsamplingFactor_shouldTakeTheLargerOfWidthAndPixelBudget() {
		assertThat(ImageInspector.subsamplingFactor(7728, 5152, 960)).isEqualTo(4);
		// 너비만 보면 1이지만 1억 픽셀이라 픽셀 예산으로 줄인다.
		assertThat(ImageInspector.subsamplingFactor(2000, 50000, 960)).isGreaterThanOrEqualTo(5);
		assertThat(ImageInspector.subsamplingFactor(400, 300, 960)).isEqualTo(1);
	}

	@Test
	void inspect_shouldReadDimensionsFromHeader() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB), "png", out);

		ImageInspector.ImageDimension dimension = imageInspector.inspect(new ByteArrayInputStream(out.toByteArray()));

		assertThat(dimension.width()).isEqualTo(640);
		assertThat(dimension.height()).isEqualTo(480);
	}

	@Test
	void inspect_shouldRejectHeaderClaimingMoreThanMaxPixels() throws Exception {
		// 헤더만 2억 픽셀로 적은 수십 바이트짜리 PNG. 실제 픽셀 데이터는 없다.
		byte[] bomb = pngWithHeaderOnly(20000, 10000);

		assertThatThrownBy(() -> imageInspector.inspect(new ByteArrayInputStream(bomb)))
			.isInstanceOf(BadRequestException.class)
			.hasMessage("이미지 해상도가 너무 큽니다.");
	}

	@Test
	void inspect_shouldRejectBytesThatAreNotAnImage() {
		byte[] text = "not an image".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> imageInspector.inspect(new ByteArrayInputStream(text)))
			.isInstanceOf(FileExtException.class);
	}

	@Test
	void inspect_shouldRejectImageWithTruncatedHeader() {
		assertThatThrownBy(() -> imageInspector.inspect(new ByteArrayInputStream(PNG_SIGNATURE)))
			.isInstanceOf(FileExtException.class);
	}

	private static byte[] pngWithHeaderOnly(int width, int height) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(out);
		data.write(PNG_SIGNATURE);

		ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
		DataOutputStream ihdrData = new DataOutputStream(ihdr);
		ihdrData.writeBytes("IHDR");
		ihdrData.writeInt(width);
		ihdrData.writeInt(height);
		ihdrData.writeByte(8);
		ihdrData.writeByte(2);
		ihdrData.writeByte(0);
		ihdrData.writeByte(0);
		ihdrData.writeByte(0);
		byte[] chunk = ihdr.toByteArray();

		CRC32 crc = new CRC32();
		crc.update(chunk);
		data.writeInt(chunk.length - 4);
		data.write(chunk);
		data.writeInt((int)crc.getValue());
		return out.toByteArray();
	}
}

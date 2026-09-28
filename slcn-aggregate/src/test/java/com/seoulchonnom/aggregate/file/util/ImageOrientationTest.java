package com.seoulchonnom.aggregate.file.util;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageOrientationTest {
	private static final int WIDTH = 3;
	private static final int HEIGHT = 2;

	@TempDir
	Path tempDir;

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
	void apply_shouldMoveEveryPixelToItsExifDisplayPosition(int orientation) {
		BufferedImage source = numberedImage();

		BufferedImage result = ImageOrientation.apply(source, orientation);

		boolean swap = ImageOrientation.swapsDimensions(orientation);
		assertThat(result.getWidth()).isEqualTo(swap ? HEIGHT : WIDTH);
		assertThat(result.getHeight()).isEqualTo(swap ? WIDTH : HEIGHT);
		BiFunction<Integer, Integer, int[]> expected = displayPosition(orientation);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				int[] target = expected.apply(x, y);
				assertThat(result.getRGB(target[0], target[1]) & 0xFFFFFF)
					.as("orientation=%d pixel=(%d,%d)", orientation, x, y)
					.isEqualTo(source.getRGB(x, y) & 0xFFFFFF);
			}
		}
	}

	@Test
	void read_shouldReturnOrientationTagFromJpeg() throws Exception {
		Path jpeg = Files.write(tempDir.resolve("rotated.jpg"),
			JpegSegments.withExifOrientation(jpegBytes(16, 8), 6));

		assertThat(ImageOrientation.read(jpeg)).isEqualTo(6);
	}

	@Test
	void read_shouldTreatMissingTagAsNormal() throws Exception {
		Path jpeg = Files.write(tempDir.resolve("plain.jpg"), jpegBytes(16, 8));

		assertThat(ImageOrientation.read(jpeg)).isEqualTo(ImageOrientation.NORMAL);
	}

	@Test
	void read_shouldTreatUnreadableFileAsNormal() throws Exception {
		Path garbage = Files.writeString(tempDir.resolve("garbage.jpg"), "not an image", StandardCharsets.UTF_8);

		assertThat(ImageOrientation.read(garbage)).isEqualTo(ImageOrientation.NORMAL);
	}

	/**
	 * 픽셀마다 다른 색을 칠해 어느 픽셀이 어디로 갔는지 정확히 추적한다.
	 */
	private static BufferedImage numberedImage() {
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				image.setRGB(x, y, (x * 60 + 10) << 16 | (y * 100 + 20) << 8 | 0x33);
			}
		}
		return image;
	}

	/**
	 * EXIF 명세의 방향별 표시 위치. 구현의 AffineTransform과 독립적으로 좌표로 적는다.
	 */
	private static BiFunction<Integer, Integer, int[]> displayPosition(int orientation) {
		int w = WIDTH;
		int h = HEIGHT;
		return switch (orientation) {
			case 2 -> (x, y) -> new int[] {w - 1 - x, y};
			case 3 -> (x, y) -> new int[] {w - 1 - x, h - 1 - y};
			case 4 -> (x, y) -> new int[] {x, h - 1 - y};
			case 5 -> (x, y) -> new int[] {y, x};
			case 6 -> (x, y) -> new int[] {h - 1 - y, x};
			case 7 -> (x, y) -> new int[] {h - 1 - y, w - 1 - x};
			case 8 -> (x, y) -> new int[] {y, w - 1 - x};
			default -> (x, y) -> new int[] {x, y};
		};
	}

	private static byte[] jpegBytes(int width, int height) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", out);
		return out.toByteArray();
	}
}

package com.seoulchonnom.aggregate.file.util;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * EXIF 방향 태그 처리. 카메라와 휴대폰은 세로 사진을 가로 픽셀 그대로 두고 방향 태그만 붙인다.
 * ImageIO는 이 태그를 무시하므로, 파생본을 그대로 만들면 세로 사진이 누운 채로 저장된다.
 * 파생본에는 EXIF를 쓰지 않으므로 여기서 한 번 똑바로 세워 두면 브라우저가 다시 돌리지 않는다.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImageOrientation {
	public static final int NORMAL = 1;

	/**
	 * 태그가 없거나 읽을 수 없으면 NORMAL이다. 방향 판독 실패로 업로드를 실패시키지 않는다.
	 */
	public static int read(Path path) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(path.toFile());
			ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return NORMAL;
			}
			int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
			return orientation >= 1 && orientation <= 8 ? orientation : NORMAL;
		} catch (ImageProcessingException | IOException | MetadataException | RuntimeException e) {
			log.warn("EXIF orientation unreadable, treating as normal. path={}", path, e);
			return NORMAL;
		}
	}

	/**
	 * 5~8은 90°/270° 회전 계열이라 화면에 보이는 가로·세로가 픽셀 배열과 반대다.
	 */
	public static boolean swapsDimensions(int orientation) {
		return orientation >= 5 && orientation <= 8;
	}

	public static BufferedImage apply(BufferedImage source, int orientation) {
		if (orientation <= NORMAL || orientation > 8) {
			return source;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		boolean swap = swapsDimensions(orientation);
		int targetWidth = swap ? height : width;
		int targetHeight = swap ? width : height;
		int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

		BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.drawImage(source, transformFor(orientation, width, height), null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	/**
	 * EXIF 방향값별로 원본 좌표 (x, y)를 화면 좌표로 옮기는 변환.
	 * 인자 순서는 AffineTransform(m00, m10, m01, m11, m02, m12)다.
	 */
	private static AffineTransform transformFor(int orientation, int width, int height) {
		return switch (orientation) {
			// 좌우 반전: (x, y) → (w - x, y)
			case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
			// 180° 회전: (x, y) → (w - x, h - y)
			case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
			// 상하 반전: (x, y) → (x, h - y)
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
			// 대각선 반전(transpose): (x, y) → (y, x)
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
			// 시계 방향 90°: (x, y) → (h - y, x)
			case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
			// 반대 대각선 반전(transverse): (x, y) → (h - y, w - x)
			case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
			// 반시계 방향 90°: (x, y) → (y, w - x)
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
			default -> new AffineTransform();
		};
	}
}

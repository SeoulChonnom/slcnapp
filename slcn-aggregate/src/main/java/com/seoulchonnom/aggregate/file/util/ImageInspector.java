package com.seoulchonnom.aggregate.file.util;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import com.seoulchonnom.aggregate.common.exception.BadRequestException;
import com.seoulchonnom.aggregate.file.exception.FileExtException;

/**
 * 원본을 전체 해상도로 디코딩하지 않고 이미지를 다룬다.
 * 40 MP 카메라 JPG를 그대로 읽으면 한 장에 160 MB가 힙에 올라가므로, 검증은 헤더만 읽고
 * 파생본은 축소 디코딩한 결과로 만든다.
 */
@Component
public class ImageInspector {
	/**
	 * 축소 디코딩 결과의 최대 픽셀 수. INT_RGB 기준 약 16 MB다.
	 * 너비만 기준으로 삼으면 세로로 긴 이미지가 전체 해상도로 디코딩되므로 픽셀 수로도 묶는다.
	 */
	static final long DECODE_PIXEL_BUDGET = 4_000_000L;

	/**
	 * 헤더에서 픽셀 크기만 읽는다. 픽셀 데이터는 디코딩하지 않는다.
	 * 읽을 수 있는 이미지가 아니면 FileExtException, 압축 폭탄 수준으로 크면 400이다.
	 */
	public ImageDimension inspect(InputStream inputStream) throws IOException {
		try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
			return readDimension(imageInputStream);
		}
	}

	public ImageDimension inspect(Path path) throws IOException {
		try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(path.toFile())) {
			return readDimension(imageInputStream);
		}
	}

	/**
	 * 가장 큰 파생본을 만들기에 충분한 크기로만 디코딩하고, EXIF 방향대로 똑바로 세운다.
	 * 내장 ICC 프로필은 JDK JPEG 리더가 디코딩하면서 sRGB로 변환하므로 따로 처리하지 않는다.
	 * 반환하는 크기는 축소본이 아니라 원본 크기이고, 화면에 보이는 방향 기준이다.
	 * 헤더 값을 그대로 쓰면 세로 사진의 가로·세로가 뒤집혀 클라이언트 레이아웃이 틀어진다.
	 */
	public ScaledImage readScaled(Path path, int minWidth) throws IOException {
		int orientation = ImageOrientation.read(path);
		try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(path.toFile())) {
			ImageReader reader = readerFor(imageInputStream);
			try {
				reader.setInput(imageInputStream, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				int factor = subsamplingFactor(width, height, minWidth);

				ImageReadParam param = reader.getDefaultReadParam();
				param.setSourceSubsampling(factor, factor, 0, 0);
				BufferedImage upright = ImageOrientation.apply(reader.read(0, param), orientation);
				return ImageOrientation.swapsDimensions(orientation)
					? new ScaledImage(upright, height, width)
					: new ScaledImage(upright, width, height);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * 너비 기준은 결과 너비가 가장 큰 파생본의 두 배 이상 남게 해 기존 절반씩 축소 품질을 유지한다.
	 * 픽셀 예산 기준은 결과가 DECODE_PIXEL_BUDGET을 넘지 않게 한다. 둘 중 더 많이 줄이는 쪽을 쓴다.
	 */
	static int subsamplingFactor(int width, int height, int minWidth) {
		int byWidth = Math.max(1, width / (minWidth * 2));
		double pixels = (double)width * height;
		int byPixels = Math.max(1, (int)Math.ceil(Math.sqrt(pixels / DECODE_PIXEL_BUDGET)));
		return Math.max(byWidth, byPixels);
	}

	private ImageDimension readDimension(ImageInputStream imageInputStream) throws IOException {
		ImageReader reader = readerFor(imageInputStream);
		int width;
		int height;
		try {
			reader.setInput(imageInputStream, true, true);
			width = reader.getWidth(0);
			height = reader.getHeight(0);
		} catch (IOException | RuntimeException e) {
			// 시그니처는 맞지만 헤더가 깨진 파일이다. 이미지로 쓸 수 없으므로 확장자 오류와 같게 다룬다.
			throw new FileExtException();
		} finally {
			reader.dispose();
		}

		if (width <= 0 || height <= 0) {
			throw new FileExtException();
		}
		if ((long)width * height > MAX_IMAGE_PIXELS) {
			throw new BadRequestException(FILE_PIXEL_ERROR_MESSAGE);
		}
		return new ImageDimension(width, height);
	}

	private ImageReader readerFor(ImageInputStream imageInputStream) {
		if (imageInputStream == null) {
			throw new FileExtException();
		}

		Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
		if (!readers.hasNext()) {
			throw new FileExtException();
		}
		return readers.next();
	}

	public record ImageDimension(int width, int height) {
	}

	/**
	 * 똑바로 세운 축소 이미지와, 화면에 보이는 방향 기준의 원본 크기.
	 */
	public record ScaledImage(BufferedImage image, int displayWidth, int displayHeight) {
	}
}

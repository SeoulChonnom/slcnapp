package com.seoulchonnom.aggregate.file.util;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.FileReference;
import com.seoulchonnom.spec.file.entity.vo.FileType;

class FileUtilsTest {
	private static final byte[] PNG_BYTES = Base64.getDecoder()
		.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");

	private final FileUtils fileUtils = new FileUtils();

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(tempDir.resolve("logo"));
		Files.createDirectories(tempDir.resolve("travel"));
		ReflectionTestUtils.setField(fileUtils, "directory", tempDir + "/");
	}

	@Test
	void saveImages_shouldAcceptUppercaseImageExtensionWhenContentIsValid() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "image.PNG", "image/png", PNG_BYTES);

		FileReference fileReference = fileUtils.saveImages(file, "logo");

		assertThat(fileReference.getType()).isEqualTo(FileType.LOGO);
		assertThat(fileReference.getFilename()).endsWith(".png");
	}

	@Test
	void saveImageAsset_shouldCreateTravelDirectoryAndReturnFileAsset() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "travel.PNG", "image/png", PNG_BYTES);

		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		assertThat(fileAsset.getType()).isEqualTo(FileType.TRAVEL);
		assertThat(fileAsset.getOriginalFilename()).isEqualTo("travel.PNG");
		assertThat(fileAsset.getStoredFilename()).endsWith(".png");
		assertThat(fileAsset.getPath()).isEqualTo("travel/" + fileAsset.getStoredFilename());
		assertThat(Files.exists(tempDir.resolve(fileAsset.getPath()))).isTrue();
	}

	@Test
	void saveImages_shouldRejectFileWhenContentTypeIsNotImage() {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "text/plain", PNG_BYTES);

		assertThatThrownBy(() -> fileUtils.saveImages(file, "logo"))
			.isInstanceOf(FileExtException.class);
	}

	@Test
	void saveImages_shouldRejectFileWhenBytesAreNotImage() {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "not-image".getBytes());

		assertThatThrownBy(() -> fileUtils.saveImages(file, "logo"))
			.isInstanceOf(FileExtException.class);
	}

	@Test
	void writeVariants_shouldGenerateSmallerVariantsAndReportOriginalDimensions() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", pngBytes(1600, 900));
		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		var profile = fileUtils.writeVariants(fileAsset);

		assertThat(profile.width()).isEqualTo(1600);
		assertThat(profile.height()).isEqualTo(900);
		assertThat(profile.variants()).extracting(FileVariant::getVariant)
			.containsExactly("home-feature", "home-thumb");

		Path feature = variantPath(profile, "home-feature");
		Path thumb = variantPath(profile, "home-thumb");
		assertThat(Files.exists(feature)).isTrue();
		assertThat(Files.exists(thumb)).isTrue();

		BufferedImage featureImage = ImageIO.read(feature.toFile());
		assertThat(featureImage.getWidth()).isEqualTo(960);
		assertThat(featureImage.getHeight()).isEqualTo(540);
		BufferedImage thumbImage = ImageIO.read(thumb.toFile());
		assertThat(thumbImage.getWidth()).isEqualTo(320);
		assertThat(thumbImage.getHeight()).isEqualTo(180);
		assertThat(Files.size(thumb)).isLessThan(Files.size(feature));
	}

	@Test
	void writeVariants_shouldRecordMimeTypeMatchingTheFileItActuallyWrote() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", pngBytes(1600, 900));
		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		var profile = fileUtils.writeVariants(fileAsset);

		assertThat(profile.variants()).allSatisfy(variant -> {
			String extension = variant.getFilename().substring(variant.getFilename().lastIndexOf('.') + 1);
			assertThat(variant.getMimeType()).isEqualTo("image/" + ("jpg".equals(extension) ? "jpeg" : extension));
		});
	}

	@Test
	void writeVariants_shouldPreferWebpWhenTheEncoderIsAvailable() throws Exception {
		assumeTrue(ImageIO.getImageWritersByFormatName("webp").hasNext(), "WebP writer not registered");
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", pngBytes(1600, 900));
		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		var profile = fileUtils.writeVariants(fileAsset);

		assertThat(profile.variants()).allSatisfy(variant -> {
			assertThat(variant.getFilename()).endsWith(".webp");
			assertThat(variant.getMimeType()).isEqualTo("image/webp");
		});
	}

	@Test
	void writeVariants_shouldFallBackToJpegWhenWebpEncoderIsUnavailable() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", pngBytes(1600, 900));
		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		List<ImageWriterSpi> removed = deregisterWebpWriters();
		try {
			assertThat(ImageIO.getImageWritersByFormatName("webp").hasNext()).isFalse();

			var profile = fileUtils.writeVariants(fileAsset);

			assertThat(profile.variants()).isNotEmpty();
			assertThat(profile.variants()).allSatisfy(variant -> {
				assertThat(variant.getFilename()).endsWith(".jpg");
				assertThat(variant.getMimeType()).isEqualTo("image/jpeg");
			});
			// 실패한 WebP 시도가 빈 파일을 남기지 않아야 한다.
			assertThat(Files.exists(variantPath(profile, "home-thumb"))).isTrue();
			String base = fileAsset.getStoredFilename().replace(".png", "");
			assertThat(Files.exists(tempDir.resolve("travel").resolve(base + "_home-thumb.webp"))).isFalse();
		} finally {
			removed.forEach(IIORegistry.getDefaultInstance()::registerServiceProvider);
		}
	}

	private Path variantPath(FileUtils.ImageProfile profile, String variantName) {
		String filename = profile.variants().stream()
			.filter(variant -> variantName.equals(variant.getVariant()))
			.map(FileVariant::getFilename)
			.findFirst()
			.orElseThrow();
		return tempDir.resolve("travel").resolve(filename);
	}

	private static List<ImageWriterSpi> deregisterWebpWriters() {
		IIORegistry registry = IIORegistry.getDefaultInstance();
		List<ImageWriterSpi> removed = new ArrayList<>();
		registry.getServiceProviders(ImageWriterSpi.class, false).forEachRemaining(spi -> {
			for (String formatName : spi.getFormatNames()) {
				if ("webp".equalsIgnoreCase(formatName)) {
					removed.add(spi);
					return;
				}
			}
		});
		removed.forEach(spi -> registry.deregisterServiceProvider(spi, ImageWriterSpi.class));
		return removed;
	}

	@Test
	void writeVariants_shouldSkipVariantsThatWouldUpscaleTheOriginal() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "small.png", "image/png", pngBytes(400, 300));
		var fileAsset = fileUtils.saveImageAsset(file, "travel");

		var profile = fileUtils.writeVariants(fileAsset);

		assertThat(profile.width()).isEqualTo(400);
		assertThat(profile.variants()).extracting(FileVariant::getVariant).containsExactly("home-thumb");
	}

	@Test
	void writeVariants_shouldReturnEmptyProfileInsteadOfFailingWhenImageIsUnreadable() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.writeString(tempDir.resolve("travel/broken.svg"), "<svg></svg>");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "broken.svg", "broken.svg", "image/svg+xml", 11L);

		var profile = fileUtils.writeVariants(fileAsset);

		assertThat(profile.width()).isZero();
		assertThat(profile.variants()).isEmpty();
	}

	@Test
	void existsFileRef_shouldReflectFilePresence() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/present.png"), PNG_BYTES);

		assertThat(fileUtils.existsFileRef("travel", "present.png")).isTrue();
		assertThat(fileUtils.existsFileRef("travel", "absent.png")).isFalse();
	}

	@Test
	void isValidFileRef_shouldAcceptVariantFilenames() {
		fileUtils.isValidFileRef("travel", "72d768d4-2b05-48f9-bee8-fee3b52e909f_home-thumb.jpg");
		fileUtils.isValidFileRef("travel", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png");
	}

	@Test
	void isValidFileRef_shouldStillRejectTraversalAndUnknownShapes() {
		assertThatThrownBy(() -> fileUtils.isValidFileRef("travel", "../secret.png"))
			.isInstanceOf(FilePathInvalidException.class);
		assertThatThrownBy(() -> fileUtils.isValidFileRef("travel", "not-a-uuid_home-thumb.jpg"))
			.isInstanceOf(FilePathInvalidException.class);
	}

	private static byte[] pngBytes(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setPaint(new GradientPaint(0, 0, Color.RED, width, height, Color.BLUE));
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}
}

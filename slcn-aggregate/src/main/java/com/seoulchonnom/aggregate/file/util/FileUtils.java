package com.seoulchonnom.aggregate.file.util;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.file.exception.FileExtException;
import com.seoulchonnom.aggregate.file.exception.FilePathInvalidException;
import com.seoulchonnom.aggregate.file.exception.FileSizeException;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;
import com.seoulchonnom.spec.file.entity.vo.ImageFormat;
import com.seoulchonnom.spec.file.entity.vo.ImageVariant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FileUtils {
	/**
	 * 원본을 임시 디렉터리에 받아둔다. 최종 저장 위치가 서버 디스크가 아니므로 업로드 경로에 바로 쓰지 않는다.
	 * 임시 디렉터리는 오브젝트 업로드가 끝난 뒤 호출자가 deleteStaging으로 지운다.
	 */
	public StagedUpload stageUpload(MultipartFile multipartFile, String type) throws IOException {
		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH)) {
			throw new FilePathInvalidException();
		}

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileSizeException();
		}

		validateImageFile(multipartFile);

		String filename = createSaveFileName(multipartFile.getOriginalFilename());
		Path stagingDirectory = Files.createTempDirectory("slcn-upload-");
		Path originalPath = stagingDirectory.resolve(filename);
		multipartFile.transferTo(originalPath);

		FileAsset fileAsset = new FileAsset(
			FileType.from(type),
			multipartFile.getOriginalFilename(),
			filename,
			multipartFile.getContentType(),
			multipartFile.getSize()
		);
		return new StagedUpload(fileAsset, stagingDirectory, originalPath);
	}

	/**
	 * 임시 디렉터리를 통째로 지운다. 정리 실패가 업로드를 실패시키면 안 되므로 로그만 남긴다.
	 */
	public void deleteStaging(Path stagingDirectory) {
		if (stagingDirectory == null) {
			return;
		}

		try (Stream<Path> paths = Files.walk(stagingDirectory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
		} catch (IOException e) {
			log.warn("Failed to clean staging directory. path={}", stagingDirectory, e);
		}
	}

	/**
	 * 임시 디렉터리의 원본에서 홈 화면용 파생본을 만들고 원본 픽셀 크기를 함께 읽는다.
	 * 파생본은 원본과 같은 임시 디렉터리에 쓰이고, 업로드는 호출자가 한다.
	 * 파생본 생성은 부가 작업이므로 실패해도 예외를 던지지 않는다. 업로드 자체는 성공해야 한다.
	 */
	public ImageProfile writeVariants(FileAsset fileAsset, Path originalPath) {
		BufferedImage source = readImage(originalPath);
		if (source == null) {
			log.warn("Variant generation skipped: unreadable image. path={}", fileAsset.getPath());
			return ImageProfile.empty();
		}

		int width = source.getWidth();
		int height = source.getHeight();
		List<FileVariant> generated = new ArrayList<>();

		for (ImageVariant variant : ImageVariant.values()) {
			if (width <= variant.getWidth()) {
				continue;
			}

			writeVariant(source, originalPath.getParent(), fileAsset, variant).ifPresent(generated::add);
		}

		return new ImageProfile(width, height, generated);
	}

	public void isValidFilePath(String path) {
		if (path == null || path.isEmpty() || !path.matches(FILE_PATH_REGEX_STRING)) {
			throw new FilePathInvalidException();
		}
	}

	public void isValidFileRef(String type, String filename) {
		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH) ||
			filename == null || filename.isEmpty() || !filename.matches(FILE_NAME_REGEX_STRING)) {
			throw new FilePathInvalidException();
		}
	}

	private BufferedImage readImage(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * ImageFormat 선언 순서대로 시도한다. WebP 인코더는 네이티브 라이브러리에 의존하므로
	 * 사용할 수 없는 환경에서는 JPEG로 내려가고, 그래도 실패하면 이 파생본만 포기한다.
	 */
	private Optional<FileVariant> writeVariant(BufferedImage source, Path targetDirectory, FileAsset fileAsset,
		ImageVariant variant) {
		BufferedImage scaled = scaleToWidth(source, variant.getWidth());

		for (ImageFormat format : ImageFormat.values()) {
			String filename = fileAsset.variantFilename(variant, format.getExtension());
			Path targetPath = targetDirectory.resolve(filename);
			try {
				Files.createDirectories(targetDirectory);
				writeImage(scaled, targetPath, format);
				return Optional.of(new FileVariant(variant.getValue(), filename, format.getMimeType()));
			} catch (IOException | RuntimeException e) {
				// 부분적으로 쓰인 파일이 남으면 조회 때 깨진 이미지를 응답하게 된다.
				deleteQuietly(targetPath);
				log.warn("Variant encoding failed, trying next format. path={}, variant={}, format={}",
					fileAsset.getPath(), variant.getValue(), format.getFormatName(), e);
			}
		}

		log.warn("Variant generation gave up for every format. path={}, variant={}",
			fileAsset.getPath(), variant.getValue());
		return Optional.empty();
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("Failed to remove incomplete variant file. path={}", path, e);
		}
	}

	/**
	 * 한 번에 크게 줄이면 계단 현상이 생기므로 절반씩 내려간 뒤 마지막에 목표 크기로 맞춘다.
	 */
	private BufferedImage scaleToWidth(BufferedImage source, int targetWidth) {
		BufferedImage current = source;
		int currentWidth = source.getWidth();
		int currentHeight = source.getHeight();

		while (currentWidth / 2 > targetWidth) {
			currentWidth = currentWidth / 2;
			currentHeight = Math.max(1, currentHeight / 2);
			current = redraw(current, currentWidth, currentHeight);
		}

		int targetHeight = Math.max(1,
			Math.round(source.getHeight() * (targetWidth / (float)source.getWidth())));
		return redraw(current, targetWidth, targetHeight);
	}

	private BufferedImage redraw(BufferedImage source, int width, int height) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			// JPEG는 알파 채널이 없으므로 투명 영역을 흰색으로 깔아준다.
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private void writeImage(BufferedImage image, Path target, ImageFormat format) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format.getFormatName());
		if (!writers.hasNext()) {
			throw new IOException("No ImageIO writer registered for format: " + format.getFormatName());
		}

		ImageWriter writer = writers.next();
		ImageWriteParam writeParam = writer.getDefaultWriteParam();
		if (writeParam.canWriteCompressed()) {
			writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			// WebP writer는 압축 타입을 먼저 골라야 품질 설정을 받아준다.
			String[] compressionTypes = writeParam.getCompressionTypes();
			if (compressionTypes != null && compressionTypes.length > 0 && writeParam.getCompressionType() == null) {
				writeParam.setCompressionType(compressionTypes[0]);
			}
			writeParam.setCompressionQuality(VARIANT_COMPRESSION_QUALITY);
		}

		try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(target.toFile())) {
			writer.setOutput(outputStream);
			writer.write(null, new IIOImage(image, null, null), writeParam);
		} finally {
			writer.dispose();
		}
	}

	private String createSaveFileName(String originalFilename) {
		String ext = extractExt(originalFilename);
		String uuid = UUID.randomUUID().toString();
		return uuid + '.' + ext;
	}

	private String extractExt(String originalFilename) {
		if (!StringUtils.hasText(originalFilename)) {
			throw new FileExtException();
		}

		int pos = originalFilename.lastIndexOf(".");
		if (pos < 0 || pos == originalFilename.length() - 1) {
			throw new FileExtException();
		}
		return originalFilename.substring(pos + 1).toLowerCase(Locale.ROOT);
	}

	private void validateImageFile(MultipartFile multipartFile) throws IOException {
		String ext = extractExt(multipartFile.getOriginalFilename());
		if (!ext.matches(EXT_REGEX_STRING)) {
			throw new FileExtException();
		}

		String contentType = multipartFile.getContentType();
		if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new FileExtException();
		}

		if ("svg".equals(ext)) {
			validateSvg(multipartFile);
			return;
		}

		try (InputStream inputStream = multipartFile.getInputStream()) {
			BufferedImage image = ImageIO.read(inputStream);
			if (image == null) {
				throw new FileExtException();
			}
		}
	}

	private void validateSvg(MultipartFile multipartFile) throws IOException {
		byte[] header;
		try (InputStream inputStream = multipartFile.getInputStream()) {
			header = inputStream.readNBytes(1024);
		}

		String content = new String(header, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		if (!content.contains("<svg")) {
			throw new FileExtException();
		}
	}

	/**
	 * 임시 디렉터리에 받아둔 업로드 한 건.
	 */
	public record StagedUpload(FileAsset fileAsset, Path stagingDirectory, Path originalPath) {
	}

	/**
	 * 원본 픽셀 크기와 실제로 생성된 파생본 이름.
	 */
	public record ImageProfile(int width, int height, List<FileVariant> variants) {
		public static ImageProfile empty() {
			return new ImageProfile(0, 0, List.of());
		}
	}
}

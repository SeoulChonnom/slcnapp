package com.seoulchonnom.aggregate.file.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.file.storage.ObjectKeys;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 디스크에 이미 쌓인 원본과 파생본을 오브젝트 스토리지로 옮긴다.
 * 이미 올라간 객체는 건너뛰므로 몇 번을 다시 돌려도 안전하고, 중간에 끊겨도 이어서 돌리면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAssetMigrationLogic {
	private final FileAssetStore fileAssetStore;
	private final ObjectStorage objectStorage;

	/**
	 * 이전 저장 레이아웃({type}/{filename})의 기준 디렉터리. 백필이 끝나면 더 이상 읽지 않는다.
	 */
	@Value("${slcn.upload.path}")
	private String legacyDirectory;

	@Value("${slcn.storage.migration.batch-size:100}")
	private int batchSize;

	public MigrationReport migrateLegacyFiles() {
		int uploaded = 0;
		int skipped = 0;
		int missing = 0;
		int failed = 0;

		for (int page = 0; ; page++) {
			List<FileAsset> assets = fileAssetStore.findPage(page, batchSize);
			if (assets.isEmpty()) {
				break;
			}

			for (FileAsset asset : assets) {
				for (MigrationOutcome outcome : migrateAsset(asset)) {
					switch (outcome) {
						case UPLOADED -> uploaded++;
						case SKIPPED -> skipped++;
						case MISSING -> missing++;
						case FAILED -> failed++;
					}
				}
			}

			// 마지막 페이지가 정확히 batchSize로 떨어지지 않는 한 여기서 끝난다. 빈 페이지 조회를 한 번 아낀다.
			if (assets.size() < batchSize) {
				break;
			}
		}

		return new MigrationReport(uploaded, skipped, missing, failed);
	}

	private List<MigrationOutcome> migrateAsset(FileAsset asset) {
		String type = asset.getType().getValue();
		List<MigrationOutcome> outcomes = new ArrayList<>();

		outcomes.add(migrateOne(ObjectKeys.original(type, asset.getStoredFilename()), type,
			asset.getStoredFilename(), asset.getMimeType()));
		for (FileVariant variant : asset.getVariants()) {
			outcomes.add(migrateOne(ObjectKeys.derived(type, variant.getFilename()), type,
				variant.getFilename(), variant.getMimeType()));
		}

		return outcomes;
	}

	private MigrationOutcome migrateOne(String key, String type, String filename, String contentType) {
		if (objectStorage.exists(key)) {
			return MigrationOutcome.SKIPPED;
		}

		Path source = Paths.get(legacyDirectory).resolve(type).resolve(filename).normalize();
		if (!Files.exists(source)) {
			log.warn("Legacy file is missing, nothing to migrate. key={}, path={}", key, source);
			return MigrationOutcome.MISSING;
		}

		try {
			objectStorage.put(key, source, contentType);
			return MigrationOutcome.UPLOADED;
		} catch (IOException e) {
			// 한 건이 실패해도 나머지는 계속 옮긴다. 실패분은 다시 돌리면 이어서 처리된다.
			log.error("Failed to migrate legacy file. key={}, path={}", key, source, e);
			return MigrationOutcome.FAILED;
		}
	}

	public enum MigrationOutcome {
		UPLOADED, SKIPPED, MISSING, FAILED
	}

	/**
	 * 옮긴 결과. 배포 로그 한 줄로 확인할 수 있어야 하므로 개수만 센다.
	 */
	public record MigrationReport(int uploaded, int skipped, int missing, int failed) {
	}
}

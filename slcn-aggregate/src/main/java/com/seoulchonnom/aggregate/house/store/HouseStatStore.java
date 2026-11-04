package com.seoulchonnom.aggregate.house.store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.seoulchonnom.aggregate.house.stat.HouseStatWindows;
import com.seoulchonnom.aggregate.house.stat.RankingRow;
import com.seoulchonnom.aggregate.house.store.jpo.HouseComplexAreaStatId;
import com.seoulchonnom.aggregate.house.store.projection.AreaStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.CompareStatPdo;
import com.seoulchonnom.aggregate.house.store.projection.RankingInputPdo;
import com.seoulchonnom.aggregate.house.store.projection.RankingRowPdo;
import com.seoulchonnom.aggregate.house.store.repository.HouseComplexAreaStatRepository;
import com.seoulchonnom.aggregate.house.store.repository.HouseRankingRepository;
import com.seoulchonnom.spec.house.entity.vo.AreaBucket;
import com.seoulchonnom.spec.house.entity.vo.RankingBadge;
import com.seoulchonnom.spec.house.entity.vo.RankingType;

import lombok.RequiredArgsConstructor;

/**
 * 파생 통계·랭킹 테이블. 쓰기는 통째로 다시 만드는 것뿐이고, 읽기는 Rdo 조립용 Pdo로 돌려준다.
 */
@Repository
@RequiredArgsConstructor
public class HouseStatStore {
	private final HouseComplexAreaStatRepository houseComplexAreaStatRepository;
	private final HouseRankingRepository houseRankingRepository;

	/**
	 * 통계를 지우고 거래에서 다시 만든다. 호출하는 쪽의 트랜잭션 안에서 불러야 중간 상태가 보이지 않는다.
	 *
	 * @return 만든 행 수
	 */
	public int rebuildAreaStats(HouseStatWindows windows) {
		houseComplexAreaStatRepository.deleteAllStats();
		return houseComplexAreaStatRepository.insertStats(windows.today(), windows.w3mStart(), windows.w6mStart(),
			windows.w12mStart(), windows.w3m1yStart(), windows.w3m1yEnd(), windows.w3m3yStart(), windows.w3m3yEnd());
	}

	public Optional<AreaStatPdo> findAreaStat(String complexId, AreaBucket areaBucket) {
		return houseComplexAreaStatRepository.findById(new HouseComplexAreaStatId(complexId, areaBucket.name()))
			.map(stat -> new AreaStatPdo(stat.getRecentPriceManwon(), stat.getRecentWindowMonths(),
				stat.getRecentSample()));
	}

	/**
	 * 단지들의 (단지, 구간) 통계 행을 한꺼번에. 통계가 없는 조합은 결과에 없다.
	 */
	public List<CompareStatPdo> findCompareStats(Collection<String> complexIds) {
		if (complexIds == null || complexIds.isEmpty()) {
			return List.of();
		}
		return houseComplexAreaStatRepository.findByComplexIdIn(complexIds).stream()
			.map(stat -> new CompareStatPdo(stat.getComplexId(), AreaBucket.valueOf(stat.getAreaBucket()),
				stat.getAvgArea12m(), stat.getRecentPriceManwon(), stat.getRecentWindowMonths(),
				stat.getRecentSample(), stat.getAvg3m(), stat.getCnt3m(), stat.getAvg3m1yAgo(),
				stat.getCnt3m1yAgo(), stat.getChange1yPct(), stat.getChange1yManwon(), stat.getAvg3m3yAgo(),
				stat.getCnt3m3yAgo(), stat.getChange3yPct(), stat.getChange3yManwon()))
			.toList();
	}

	/**
	 * 랭킹 계산 입력: 모든 (단지, 구간) 통계 행에 단지의 구 코드·이름을 붙인 것.
	 */
	public List<RankingInputPdo> findRankingInputs() {
		return houseRankingRepository.findRankingInputs().stream()
			.map(row -> new RankingInputPdo((String)row[0], AreaBucket.valueOf((String)row[1]), (String)row[2],
				(String)row[3], ((Number)row[4]).intValue(), toLong(row[5]), ((Number)row[6]).intValue(),
				((Number)row[7]).intValue(), ((Number)row[8]).intValue(), toDecimal(row[9]), toLong(row[10])))
			.toList();
	}

	/**
	 * 랭킹을 지우고 주어진 행으로 다시 채운다. 호출하는 쪽의 트랜잭션 안에서 불러야 중간 상태가 보이지 않는다.
	 *
	 * @return 넣은 행 수
	 */
	public int replaceRankings(List<RankingRow> rows, LocalDate computedDate) {
		houseRankingRepository.deleteAllRows();
		if (rows.isEmpty()) {
			return 0;
		}
		return houseRankingRepository.insertAll(join(rows, row -> row.type().name()),
			join(rows, row -> row.area().name()), join(rows, row -> String.valueOf(row.rankNo())),
			join(rows, RankingRow::lawdCd), join(rows, row -> orEmpty(row.complexId())),
			join(rows, row -> row.value() == null ? "" : row.value().toPlainString()),
			join(rows, row -> row.changeManwon() == null ? "" : String.valueOf(row.changeManwon())),
			join(rows, row -> String.valueOf(row.sampleCount())),
			join(rows, row -> row.badge() == null ? "" : row.badge().name()),
			join(rows, row -> row.periodFrom().toString()), join(rows, row -> row.periodTo().toString()),
			computedDate);
	}

	/**
	 * 랭킹 한 판을 순위 순으로. 랭킹이 만들어진 적 없으면 빈 목록이다.
	 */
	public List<RankingRowPdo> findRankings(RankingType type, AreaBucket area) {
		return houseRankingRepository.findRankingRows(type.name(), area.name()).stream()
			.map(row -> new RankingRowPdo(((Number)row[0]).intValue(), (String)row[1], (String)row[2], (String)row[3],
				toDouble(row[4]), toDouble(row[5]), toInteger(row[6]), toDecimal(row[7]), toInteger(row[8]),
				toDecimal(row[9]), toLong(row[10]), ((Number)row[11]).intValue(),
				row[12] == null ? null : RankingBadge.valueOf((String)row[12]), toDate(row[13]), toDate(row[14])))
			.toList();
	}

	/** 쉼표로 이어 붙인다. 값에 쉼표가 없는 컬럼만 쓴다(HouseRankingRepository.insertAll). */
	private static String join(List<RankingRow> rows, Function<RankingRow, String> column) {
		return rows.stream().map(column).collect(Collectors.joining(","));
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	private static Long toLong(Object value) {
		return value == null ? null : ((Number)value).longValue();
	}

	private static Integer toInteger(Object value) {
		return value == null ? null : ((Number)value).intValue();
	}

	private static Double toDouble(Object value) {
		return value == null ? null : ((Number)value).doubleValue();
	}

	private static BigDecimal toDecimal(Object value) {
		if (value == null) {
			return null;
		}
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
	}

	private static LocalDate toDate(Object value) {
		if (value instanceof LocalDate date) {
			return date;
		}
		return value instanceof java.sql.Date date ? date.toLocalDate() : null;
	}
}

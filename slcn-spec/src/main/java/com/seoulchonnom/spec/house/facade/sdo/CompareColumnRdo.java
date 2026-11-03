package com.seoulchonnom.spec.house.facade.sdo;

import java.util.List;

import com.seoulchonnom.spec.house.entity.vo.AreaBucket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비교표의 열 하나(단지 + 면적 구간). 값이 없는 필드는 null이고 FE가 "정보 없음"으로 보여 준다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompareColumnRdo {
	private String complexId;
	private String name;
	private String districtName;
	private String umdName;
	private AreaBucket area;
	private RecentPriceRdo recentPrice;
	private Integer households;
	private Integer buildYear;
	private Integer ageYears;
	private AreaInfoRdo areaInfo;
	private FarRdo far;
	/** 학교급 초·중·고 순. 단지에 좌표가 없거나 학교를 아직 못 받았으면 빈 배열 */
	private List<SchoolRdo> schools;
	/** 정비사업 데이터가 아직 적재되지 않았으면 null, 적재됐는데 반경 500m 안에 없으면 빈 배열 */
	private List<RedevelopmentRdo> redevelopments;
	/** 통계 행이 없으면 null */
	private PriceChangeRdo change1y;
	private PriceChangeRdo change3y;
}

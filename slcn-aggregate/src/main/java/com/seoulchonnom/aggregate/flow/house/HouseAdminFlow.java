package com.seoulchonnom.aggregate.flow.house;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.common.util.PageRequestSupport;
import com.seoulchonnom.aggregate.house.logic.HouseComplexLogic;
import com.seoulchonnom.aggregate.house.store.projection.IncompleteComplexPdo;
import com.seoulchonnom.spec.common.exception.BusinessException;
import com.seoulchonnom.spec.common.exception.ErrorCode;
import com.seoulchonnom.spec.house.entity.HouseComplex;
import com.seoulchonnom.spec.house.entity.vo.AddressOverride;
import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;
import com.seoulchonnom.spec.house.mapper.HouseAdminMapper;

import lombok.RequiredArgsConstructor;

/**
 * 내집찾기 관리자 보강 API. 입력 검증을 여기서 하고 저장은 HouseComplexLogic의 트랜잭션 메서드에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class HouseAdminFlow {
	static final int MAX_ROAD_ADDRESS_LENGTH = 200;

	private final HouseComplexLogic houseComplexLogic;
	private final HouseAdminMapper houseAdminMapper;
	private final Clock houseClock;

	/**
	 * @param page 1부터. 없으면 1
	 * @param size 없거나 0 이하면 20, 100을 넘으면 100(PageRequestSupport 규칙)
	 */
	public IncompleteComplexListRdo getIncompleteComplexes(Integer page, Integer size) {
		int pageNumber = page == null ? 1 : page;
		if (pageNumber < 1) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page는 1 이상이어야 합니다.");
		}
		int pageSize = PageRequestSupport.normalizeSize(size);
		LocalDate today = LocalDate.now(houseClock);
		List<IncompleteComplexPdo> rows = houseComplexLogic.findIncomplete(today, pageSize,
			PageRequestSupport.offsetOf(pageNumber - 1, pageSize));
		List<IncompleteComplexRdo> items = rows.stream()
			.map(row -> houseAdminMapper.toIncompleteComplexRdo(row.complex(), row.trades12m()))
			.toList();
		return new IncompleteComplexListRdo(houseComplexLogic.countIncomplete(), items);
	}

	public IncompleteComplexRdo overrideAddress(String complexId, AddressOverrideUdo request) {
		AddressOverride override = validated(request);
		HouseComplex updated = houseComplexLogic.overrideAddress(complexId, override);
		long trades12m = houseComplexLogic.countTrades12m(updated.getId(), LocalDate.now(houseClock));
		return houseAdminMapper.toIncompleteComplexRdo(updated, trades12m);
	}

	/**
	 * 셋 중 하나 이상, roadAddress는 trim 후 1~200자, bonbun/bubun은 숫자 1~4자리(0 채움 4자리로 정규화).
	 * bubun만 오는 것은 거부한다. 부번은 본번 없이는 의미가 없다.
	 */
	private AddressOverride validated(AddressOverrideUdo request) {
		if (request == null || (request.getRoadAddress() == null && request.getBonbun() == null
			&& request.getBubun() == null)) {
			throw invalid("roadAddress, bonbun, bubun 중 하나 이상이 필요합니다.");
		}
		String roadAddress = null;
		if (request.getRoadAddress() != null) {
			roadAddress = request.getRoadAddress().strip();
			if (roadAddress.isEmpty() || roadAddress.length() > MAX_ROAD_ADDRESS_LENGTH) {
				throw invalid("roadAddress는 1자 이상 " + MAX_ROAD_ADDRESS_LENGTH + "자 이하여야 합니다.");
			}
		}
		String bonbun = lotNumber("bonbun", request.getBonbun());
		String bubun = lotNumber("bubun", request.getBubun());
		if (bubun != null && bonbun == null) {
			throw invalid("bubun만 보낼 수 없습니다. bonbun도 함께 보내세요.");
		}
		return new AddressOverride(roadAddress, bonbun, bubun);
	}

	private String lotNumber(String field, String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		if (!stripped.matches("\\d{1,4}")) {
			throw invalid(field + "는 숫자 1~4자리여야 합니다.");
		}
		return "0".repeat(4 - stripped.length()) + stripped;
	}

	private BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
	}
}

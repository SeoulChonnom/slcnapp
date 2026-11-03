package com.seoulchonnom.spec.inspection.facade.sdo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressSearchRdo {
	private long totalCount;
	private List<AddressCandidateRdo> items;
}

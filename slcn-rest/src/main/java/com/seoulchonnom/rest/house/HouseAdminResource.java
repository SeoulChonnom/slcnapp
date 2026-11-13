package com.seoulchonnom.rest.house;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.seoulchonnom.aggregate.flow.house.HouseAdminFlow;
import com.seoulchonnom.spec.house.facade.HouseAdminFacade;
import com.seoulchonnom.spec.house.facade.sdo.AddressOverrideUdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexListRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteComplexRdo;
import com.seoulchonnom.spec.house.facade.sdo.IncompleteRedevelopmentListRdo;
import com.seoulchonnom.spec.house.facade.sdo.RedevelopmentImportRdo;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용(/house/admin/**, SecurityConfiguration의 ADMIN 매처).
 */
@RestController
@RequestMapping("/house/admin")
@RequiredArgsConstructor
public class HouseAdminResource implements HouseAdminFacade {
	private final HouseAdminFlow houseAdminFlow;

	@Override
	@GetMapping("/complexes/incomplete")
	public ResponseEntity<IncompleteComplexListRdo> getIncompleteComplexes(
		@RequestParam(value = "page", required = false) Integer page,
		@RequestParam(value = "size", required = false) Integer size) {
		return new ResponseEntity<>(houseAdminFlow.getIncompleteComplexes(page, size), HttpStatus.OK);
	}

	@Override
	@PutMapping("/complexes/{complexId}/address-override")
	public ResponseEntity<IncompleteComplexRdo> overrideAddress(@PathVariable("complexId") String complexId,
		@RequestBody AddressOverrideUdo request) {
		return new ResponseEntity<>(houseAdminFlow.overrideAddress(complexId, request), HttpStatus.OK);
	}

	@Override
	@PostMapping(value = "/redevelopments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<RedevelopmentImportRdo> importRedevelopments(@RequestParam("file") MultipartFile file) {
		try {
			return new ResponseEntity<>(houseAdminFlow.importRedevelopments(file.getBytes()), HttpStatus.OK);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	@GetMapping("/redevelopments/incomplete")
	public ResponseEntity<IncompleteRedevelopmentListRdo> getIncompleteRedevelopments(
		@RequestParam(value = "page", required = false) Integer page,
		@RequestParam(value = "size", required = false) Integer size) {
		return new ResponseEntity<>(houseAdminFlow.getIncompleteRedevelopments(page, size), HttpStatus.OK);
	}
}

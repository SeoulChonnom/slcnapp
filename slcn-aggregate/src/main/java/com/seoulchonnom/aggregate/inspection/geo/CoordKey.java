package com.seoulchonnom.aggregate.inspection.geo;

/**
 * 좌표제공 API 조회에 필요한 행안부 주소 식별값 5개.
 */
public record CoordKey(String admCd, String rnMgtSn, String udrtYn, String buldMnnm, String buldSlno) {
}

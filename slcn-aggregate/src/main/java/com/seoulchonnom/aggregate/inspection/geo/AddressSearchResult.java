package com.seoulchonnom.aggregate.inspection.geo;

import java.util.List;

public record AddressSearchResult(long totalCount, List<AddressCandidate> candidates) {
}

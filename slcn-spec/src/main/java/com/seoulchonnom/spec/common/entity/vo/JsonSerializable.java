package com.seoulchonnom.spec.common.entity.vo;

import java.io.Serializable;

import com.seoulchonnom.spec.common.util.JsonUtil;

public interface JsonSerializable extends Serializable {
	default String toJson() {
		return JsonUtil.toJson(this);
	}
}

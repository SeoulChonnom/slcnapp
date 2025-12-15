package com.seoulchonnom.aggregate.common.exception;

public class InternalServerErrorException extends RuntimeException {
	public InternalServerErrorException(String message) {
		super(message);
	}
}

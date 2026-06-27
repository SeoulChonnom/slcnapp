package com.seoulchonnom.aggregate.common.generator.store.entity;

public enum SequenceName {
	USER {
		@Override
		public String toString() {
			return "USER";
		}
	}, TRIP {
		@Override
		public String toString() {
			return "TRIP";
		}
	}, CALENDAR {
		@Override
		public String toString() {
			return "CALENDAR";
		}
	}, TRAVEL {
		@Override
		public String toString() {
			return "TRAVEL";
		}
	}
}

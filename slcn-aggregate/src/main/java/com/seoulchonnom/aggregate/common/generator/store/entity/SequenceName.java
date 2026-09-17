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
	}, INSPECTION_AREA {
		@Override
		public String toString() {
			return "INSPECTION_AREA";
		}
	}, INSPECTION_VISIT {
		@Override
		public String toString() {
			return "INSPECTION_VISIT";
		}
	}, INSPECTION_QUESTION {
		@Override
		public String toString() {
			return "INSPECTION_QUESTION";
		}
	}
}

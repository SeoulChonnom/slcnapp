package com.seoulchonnom.aggregate.schedule.feed.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "schedule_feed_token",
	schema = "slcn",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_schedule_feed_token_hash",
		columnNames = "token_hash"
	)
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleFeedTokenJpo extends DomainEntityJpo {
	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;
}

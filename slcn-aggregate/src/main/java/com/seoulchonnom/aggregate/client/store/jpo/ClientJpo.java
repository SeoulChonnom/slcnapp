package com.seoulchonnom.aggregate.client.store.jpo;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "client", schema = "slcn")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientJpo extends DomainEntityJpo {
	@Column(name = "client_id", nullable = false, unique = true)
	private String clientId;

	@Column(name = "client_secret", nullable = false)
	private String clientSecret;

	@Column(name = "client_name", nullable = false)
	private String clientName;

	@Column(name = "client_description")
	private String clientDescription;
}

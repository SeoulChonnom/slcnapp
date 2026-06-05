package com.seoulchonnom.aggregate.user.store.jpo;

import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user", schema = "slcn")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserJpo extends DomainEntityJpo {
	private String username;
	private String name;
	private String password;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "authority", schema = "slcn", joinColumns = @JoinColumn(name = "user_id"))
	private List<AuthorityJpo> authorityList;
}

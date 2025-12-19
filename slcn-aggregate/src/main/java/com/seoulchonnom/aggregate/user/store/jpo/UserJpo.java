package com.seoulchonnom.aggregate.user.store.jpo;

import java.util.List;

import com.seoulchonnom.aggregate.common.entity.DomainEntityJpo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserJpo extends DomainEntityJpo {
	private String username;
	private String name;
	private String password;

	@OneToMany(mappedBy = "userId", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private List<AuthorityJpo> authorityList;
}

package com.seoulchonnom.aggregate.user.store.jpo;

import com.seoulchonnom.spec.user.entity.Role;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class AuthorityJpo {
	@Id
	private String userId;
	private Role role;
	private Long registeredTime;
}

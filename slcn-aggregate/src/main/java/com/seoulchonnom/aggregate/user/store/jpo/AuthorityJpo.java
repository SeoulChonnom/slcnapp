package com.seoulchonnom.aggregate.user.store.jpo;

import com.seoulchonnom.spec.user.entity.Role;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class AuthorityJpo {
	@Enumerated(EnumType.STRING)
	private Role role;
	private Long registeredTime;
}

package com.seoulchonnom.slcnapp.user.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(length = 30, nullable = false, unique = true)
	private String username;

	@Column(length = 30, nullable = false)
	private String name;

	@Column(nullable = false)
	private String password;

	@Builder.Default
	@OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private List<Authority> authorityList = new ArrayList<>();
	private LocalDateTime lastLoginTime;

	@ColumnDefault("0")
	private Integer loginFailCount;

	private LocalDateTime lastLoginFailTime;

	public void updateLoginFailCount() {
		this.loginFailCount = this.loginFailCount + 1;
		this.lastLoginFailTime = LocalDateTime.now();
	}

	public void resetLoginFailCount() {
		this.loginFailCount = 0;
		this.lastLoginTime = LocalDateTime.now();
	}
}

package com.schoollink.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** NFR-7: passwords are hashed with BCrypt. Shared so that people (account creation) and auth (login) agree. */
@Configuration(proxyBeanMethods = false)
class PasswordConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}

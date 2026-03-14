package com.localmesalevel.aisystemtakeone.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
		    .csrf(csrf ->
		        csrf
		            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
		    );
	}
}
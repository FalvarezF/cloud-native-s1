package com.duocuc.sistema_tickets.config;

import com.duocuc.sistema_tickets.auth.UserAccount;
import com.duocuc.sistema_tickets.auth.UserAccountRepository;
import com.duocuc.sistema_tickets.auth.UserRole;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, JwtService jwtService,
			UserDetailsService userDetailsService) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
						(request, response, exception) ->
								response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado")))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/", "/health").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/sellers").hasRole(UserRole.ADMIN.name())
						.requestMatchers(HttpMethod.GET, "/api/tickets/stats").hasAnyRole(
								UserRole.ADMIN.name(), UserRole.VENDEDOR.name())
						.requestMatchers(HttpMethod.POST, "/api/tickets").hasRole(UserRole.CLIENTE.name())
						.requestMatchers(HttpMethod.POST, "/api/tickets/*/upload").hasAnyRole(
								UserRole.ADMIN.name(), UserRole.VENDEDOR.name())
						.requestMatchers(HttpMethod.PUT, "/api/tickets/*").hasAnyRole(
								UserRole.ADMIN.name(), UserRole.VENDEDOR.name())
						.requestMatchers(HttpMethod.DELETE, "/api/tickets/*").hasAnyRole(
								UserRole.ADMIN.name(), UserRole.VENDEDOR.name())
						.requestMatchers("/api/tickets", "/api/tickets/**").authenticated()
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
						UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	UserDetailsService userDetailsService(UserAccountRepository users) {
		return username -> users.findByUsername(username.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

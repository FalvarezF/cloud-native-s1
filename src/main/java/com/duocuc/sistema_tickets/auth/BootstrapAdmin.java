package com.duocuc.sistema_tickets.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(BootstrapAdmin.class);

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final String configuredUsername;
	private final String configuredPassword;

	public BootstrapAdmin(UserAccountRepository users, PasswordEncoder passwordEncoder,
			@Value("${app.security.bootstrap-admin.username:}") String username,
			@Value("${app.security.bootstrap-admin.password:}") String password) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.configuredUsername = username;
		this.configuredPassword = password;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (configuredUsername.isBlank() && configuredPassword.isBlank()) {
			logger.warn("No se configuró el administrador inicial; solo podrán registrarse clientes");
			return;
		}
		if (configuredUsername.isBlank() || configuredPassword.isBlank()) {
			throw new IllegalStateException(
					"BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD deben configurarse juntos");
		}

		String username = configuredUsername.trim().toLowerCase(Locale.ROOT);
		if (!username.matches("^[A-Za-z0-9_-]{3,80}$")) {
			throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME debe tener 3-80 caracteres válidos");
		}
		if (configuredPassword.length() < 12
				|| configuredPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalStateException("La contraseña inicial debe tener entre 12 y 72 bytes");
		}

		var existingUser = users.findByUsername(username);
		if (existingUser.isPresent()) {
			if (existingUser.get().getRole() != UserRole.ADMIN) {
				throw new IllegalStateException("El usuario de bootstrap ya existe y no tiene rol ADMIN");
			}
			return;
		}

		users.save(new UserAccount(username, passwordEncoder.encode(configuredPassword), UserRole.ADMIN));
		logger.info("Se creó la cuenta administrativa inicial '{}'", username);
	}
}

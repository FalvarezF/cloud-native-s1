package com.duocuc.sistema_tickets.auth;

import com.duocuc.sistema_tickets.config.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtService jwtService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		return toAuthResponse(createAccount(request, UserRole.CLIENTE));
	}

	@Transactional
	public UserAccountResponse createSeller(RegisterRequest request) {
		UserAccount user = createAccount(request, UserRole.VENDEDOR);
		return new UserAccountResponse(user.getId(), user.getUsername(), user.getRole());
	}

	public AuthResponse login(LoginRequest request) {
		String username = normalizeUsername(request.username());
		try {
			var authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(username, request.password()));
			return toAuthResponse((UserAccount) authentication.getPrincipal());
		} catch (AuthenticationException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
		}
	}

	private AuthResponse toAuthResponse(UserAccount user) {
		return new AuthResponse(
				jwtService.createToken(user.getUsername()),
				"Bearer",
				jwtService.getExpirationSeconds(),
				user.getId(),
				user.getUsername(),
				user.getRole());
	}

	private UserAccount createAccount(RegisterRequest request, UserRole role) {
		String username = normalizeUsername(request.username());
		if (users.existsByUsername(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está registrado");
		}
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña excede el máximo permitido");
		}
		return users.save(new UserAccount(username, passwordEncoder.encode(request.password()), role));
	}

	private String normalizeUsername(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}
}

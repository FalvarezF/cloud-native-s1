package com.duocuc.sistema_tickets.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long expirationSeconds;

	public JwtService(
			@Value("${app.security.jwt.secret}") String secret,
			@Value("${app.security.jwt.expiration-seconds:3600}") long expirationSeconds) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");
		}
		if (expirationSeconds <= 0) {
			throw new IllegalStateException("JWT_EXPIRATION_SECONDS debe ser mayor que cero");
		}
		this.signingKey = Keys.hmacShaKeyFor(secretBytes);
		this.expirationSeconds = expirationSeconds;
	}

	public String createToken(String username) {
		Instant issuedAt = Instant.now();
		return Jwts.builder()
				.subject(username)
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
				.signWith(signingKey)
				.compact();
	}

	public String extractUsername(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}
}

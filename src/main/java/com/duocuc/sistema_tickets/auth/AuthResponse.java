package com.duocuc.sistema_tickets.auth;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		String userId,
		String username,
		UserRole role) {
}

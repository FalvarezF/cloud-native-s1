package com.duocuc.sistema_tickets.controller;

import com.duocuc.sistema_tickets.model.Ticket;
import com.duocuc.sistema_tickets.auth.UserAccount;
import com.duocuc.sistema_tickets.auth.UserRole;
import com.duocuc.sistema_tickets.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

	private final TicketService service;

	public TicketController(TicketService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<List<Ticket>> generate(@Valid @RequestBody GenerateTicketsRequest request,
			@AuthenticationPrincipal UserAccount user) {
		return ResponseEntity.status(201).body(service.generate(request, user.getId()));
	}

	@GetMapping
	public List<Ticket> list(
			@RequestParam(required = false) String eventId,
			@RequestParam(required = false) String userId,
			@AuthenticationPrincipal UserAccount user) {
		return service.list(eventId, isCustomer(user) ? user.getId() : userId);
	}

	@GetMapping("/stats")
	public List<EventSalesStatsResponse> stats(@RequestParam(required = false) String eventId) {
		return service.stats(eventId);
	}

	@GetMapping("/download")
	public ResponseEntity<byte[]> downloadByFilter(
			@RequestParam(required = false) String eventId,
			@RequestParam(required = false) String userId,
			@AuthenticationPrincipal UserAccount user) {
		String effectiveUserId = isCustomer(user) ? user.getId() : userId;
		if (eventId == null && effectiveUserId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica eventId y/o userId");
		}
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("tickets.zip").build().toString())
				.body(service.downloadAsZip(eventId, effectiveUserId));
	}

	@GetMapping("/{id}")
	public Ticket get(@PathVariable String id, @AuthenticationPrincipal UserAccount user) {
		return visibleTicket(id, user);
	}

	@PostMapping("/{id}/upload")
	public Ticket upload(@PathVariable String id) {
		return service.upload(id);
	}

	@GetMapping("/{id}/download")
	public ResponseEntity<byte[]> download(@PathVariable String id, @AuthenticationPrincipal UserAccount user) {
		visibleTicket(id, user);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(id + ".pdf").build().toString())
				.body(service.download(id));
	}

	@GetMapping("/{id}/versions")
	public List<TicketVersionResponse> versions(@PathVariable String id, @AuthenticationPrincipal UserAccount user) {
		visibleTicket(id, user);
		return service.versions(id);
	}

	@PutMapping("/{id}")
	public Ticket update(@PathVariable String id, @Valid @RequestBody UpdateTicketRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

	private Ticket visibleTicket(String id, UserAccount user) {
		Ticket ticket = service.get(id);
		if (isCustomer(user) && !ticket.getUserId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado");
		}
		return ticket;
	}

	private boolean isCustomer(UserAccount user) {
		return user.getRole() == UserRole.CLIENTE;
	}
}

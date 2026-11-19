package com.duocuc.sistema_tickets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duocuc.sistema_tickets.controller.GenerateTicketsRequest;
import com.duocuc.sistema_tickets.controller.UpdateTicketRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.duocuc.sistema_tickets.model.TicketStatus;
import com.duocuc.sistema_tickets.repository.TicketRepository;
import com.duocuc.sistema_tickets.service.TicketPdfGenerator;
import com.duocuc.sistema_tickets.service.TicketService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.ResponseBytes;

@SpringBootTest(properties = {
		"app.storage.efs-directory=target/test-tickets",
		"app.aws.s3.bucket=test-bucket",
		"app.aws.s3.auto-upload=false",
		"app.security.jwt.secret=test-jwt-secret-that-is-at-least-32-bytes-long",
		"app.security.bootstrap-admin.username=test-admin",
		"app.security.bootstrap-admin.password=test-admin-password-123"
})
@AutoConfigureMockMvc
class SistemaTicketsApplicationTests {

	private static final Path TICKET_DIRECTORY = Path.of("target/test-tickets");

	@Autowired
	private TicketService service;

	@Autowired
	private TicketRepository repository;

	@Autowired
	private TicketPdfGenerator pdfGenerator;

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private S3Client s3Client;

	@BeforeEach
	void cleanTickets() throws Exception {
		repository.deleteAll();
		if (Files.exists(TICKET_DIRECTORY)) {
			try (var files = Files.list(TICKET_DIRECTORY)) {
				files.forEach(file -> {
					try {
						Files.deleteIfExists(file);
					} catch (Exception exception) {
						throw new IllegalStateException(exception);
					}
				});
			}
		}
	}

	@Test
	void generatesIndividualTicketsAndPdfFilesInConfiguredDirectory() {
		var tickets = service.generate(new GenerateTicketsRequest(
				"event-123", "Concierto", "Ana Perez", "Acceso general",
				new BigDecimal("25.50"), 2), "user-456");

		assertEquals(2, tickets.size());
		assertTrue(Files.exists(pdfGenerator.pathFor(tickets.get(0))));
		assertTrue(Files.exists(pdfGenerator.pathFor(tickets.get(1))));
		assertEquals(TicketStatus.GENERATED, tickets.get(0).getStatus());
		assertEquals(2, service.stats("event-123").get(0).ticketsGenerated());
		assertEquals(0, service.stats("event-123").get(0).ticketsUploaded());
	}

	@Test
	void uploadsDownloadsAndUpdatesStoredTicket() throws IOException {
		var ticket = service.generate(new GenerateTicketsRequest(
				"event-123", "Concierto", "Ana Perez", "Acceso general",
				new BigDecimal("25.50"), 1), "user-456").get(0);
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().versionId("version-1").build());
		byte[] storedPdf = Files.readAllBytes(pdfGenerator.pathFor(ticket));
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), storedPdf));

		var uploaded = service.upload(ticket.getId());
		assertEquals(TicketStatus.STORED, uploaded.getStatus());
		assertEquals("version-1", uploaded.getS3VersionId());
		assertEquals(storedPdf.length, service.download(ticket.getId()).length);
		try (ZipInputStream zip = new ZipInputStream(
				new ByteArrayInputStream(service.downloadAsZip("event-123", "user-456")))) {
			assertEquals(ticket.getId() + ".pdf", zip.getNextEntry().getName());
		}
		assertEquals(new BigDecimal("25.50"), service.stats("event-123").get(0).totalSales());

		service.update(ticket.getId(), new UpdateTicketRequest("Ana Actualizada", "Acceso VIP"));
		assertEquals("Ana Actualizada", service.get(ticket.getId()).getAttendeeName());
	}

	@Test
	void restApiGeneratesAndListsTickets() throws Exception {
		JsonNode customer = registerCustomer("customer-api");
		String token = customer.get("accessToken").asText();
		mockMvc.perform(post("/api/tickets")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "eventId": "event-api",
								  "eventName": "Concierto",
								  "attendeeName": "Ana Perez",
								  "details": "Acceso general",
								  "unitPrice": 15.00,
								  "quantity": 1
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$[0].status").value("GENERATED"))
				.andExpect(jsonPath("$[0].userId").value(customer.get("userId").asText()));

		mockMvc.perform(get("/api/tickets").header("Authorization", "Bearer " + token)
						.param("eventId", "event-api"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eventId").value("event-api"));
	}

	@Test
	void customersCannotReadOtherCustomersTicketsOrGlobalStats() throws Exception {
		JsonNode owner = registerCustomer("ticket-owner");
		JsonNode otherCustomer = registerCustomer("other-customer");
		var createResult = mockMvc.perform(post("/api/tickets")
						.header("Authorization", "Bearer " + owner.get("accessToken").asText())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "eventId": "private-event",
								  "eventName": "Concierto",
								  "attendeeName": "Ana Perez",
								  "details": "Acceso general",
								  "unitPrice": 15.00,
								  "quantity": 1
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		String ticketId = objectMapper.readTree(createResult.getResponse().getContentAsString())
				.get(0).get("id").asText();

		mockMvc.perform(get("/api/tickets/{id}", ticketId)
						.header("Authorization", "Bearer " + otherCustomer.get("accessToken").asText()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/tickets/stats")
						.header("Authorization", "Bearer " + otherCustomer.get("accessToken").asText()))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/tickets"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/sellers")
						.header("Authorization", "Bearer " + otherCustomer.get("accessToken").asText())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"forbidden-seller","password":"seller-password-123"}
								"""))
				.andExpect(status().isForbidden());

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"test-admin","password":"test-admin-password-123"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.get("accessToken").asText();
		mockMvc.perform(get("/api/tickets/stats").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/sellers")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"seller-api","password":"seller-password-123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("VENDEDOR"));
	}

	private JsonNode registerCustomer(String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"customer-password-123"}
								""".formatted(username)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}

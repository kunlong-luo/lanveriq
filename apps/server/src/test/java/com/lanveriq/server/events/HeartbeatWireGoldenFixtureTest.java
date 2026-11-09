package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

/**
 * Same source-controlled, invented JSON used by Rust Serde tests.
 * This proves a local wire DTO/policy/receipt contract, NOT live mTLS/PG18.
 */
class HeartbeatWireGoldenFixtureTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serdeHeartbeatRequestDeserializesAndCommitsWithExactlyTheSameRequestId()
            throws Exception {
        String requestJson = fixture("heartbeat-v1-request.json");
        String receiptJson = fixture("heartbeat-v1-receipt.json");

        DeviceHeartbeat incoming = mapper.readValue(requestJson, DeviceHeartbeat.class);
        var expectedReceipt = mapper.readTree(receiptJson);
        var device = UUID.fromString("019a3fc1-b66b-7eed-9444-7711c0ffee11");
        var expectedId = UUID.fromString(expectedReceipt.get("request_id").asText());

        assertEquals(device, incoming.deviceId());
        assertEquals(expectedId, incoming.requestId());
        assertEquals(1, incoming.schemaVersion());
        var authenticated = new VerifiedDeviceCertificate(device, "a".repeat(64));
        var policy = new DeviceHeartbeatPolicy(mapper, CLOCK);
        var accepted = policy.validate(authenticated, device, incoming);
        assertEquals("not_collected", accepted.capabilities().get("hardware_inventory"));

        var repository = Mockito.mock(DeviceHeartbeatRepository.class);
        var service = new DeviceHeartbeatService(repository, policy, CLOCK);
        var receipt = service.ingest(authenticated, device, incoming);
        Mockito.verify(repository).record(
                Mockito.eq(authenticated), Mockito.eq(device), Mockito.any());

        assertEquals(expectedId, receipt.requestId());
        assertEquals(Instant.parse(expectedReceipt.get("server_time").asText()),
                receipt.serverTime());
        var serialized = mapper.readTree(mapper.writeValueAsString(receipt));
        assertNotNull(serialized.get("server_time"));
        assertEquals(expectedId.toString(), serialized.get("request_id").asText());
    }

    private static String fixture(String name) throws Exception {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        // Supports both "gradle -p apps/server test" and repo-root invocation.
        while (directory != null) {
            Path candidate = directory.resolve("docs/testing/fixtures").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            directory = directory.getParent();
        }
        throw new AssertionError("Shared heartbeat golden fixture not checked out: " + name);
    }
}

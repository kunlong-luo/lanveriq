package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The service method must go through a genuine Spring class-proxy before
 * returning a batch ACK. A failed comparison must roll back all inserts.
 */
class DeviceEventTransactionProxyTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void successfulDatabaseVerificationCommitsBeforeCallerSeesAck() {
        var repo = Mockito.mock(EventIngestionRepository.class);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = transactionalProxy(
                new DeviceEventIngestionService(repo, mapper), manager);
        var id = UUID.randomUUID();
        var result = proxy.ingest(new VerifiedDeviceCertificate(id, "a".repeat(64)),
                id, batch(id));

        assertEquals(List.of(1L), result.acceptedSequences());
        verify(repo).appendOrVerify(Mockito.any(), Mockito.anyList());
        verify(manager).commit(status);
    }

    @Test
    void failedConflictComparisonRollsBackWithoutIssuingAck() {
        var repo = Mockito.mock(EventIngestionRepository.class);
        Mockito.doThrow(new DeviceBatchRejection(
                        DeviceBatchRejection.Reason.SEQUENCE_CONFLICT))
                .when(repo).appendOrVerify(Mockito.any(), Mockito.anyList());
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = transactionalProxy(
                new DeviceEventIngestionService(repo, mapper), manager);
        var id = UUID.randomUUID();

        assertThrows(DeviceBatchRejection.class, () -> proxy.ingest(
                new VerifiedDeviceCertificate(id, "a".repeat(64)), id, batch(id)));

        verify(manager).rollback(status);
    }

    private DeviceEventBatch batch(UUID deviceId) {
        var event = new DeviceEventBatch.Event(UUID.randomUUID(), 1, 1,
                "agent_transport", "collection_gap", 1,
                Instant.parse("2026-10-30T09:00:00Z"),
                mapper.readTree("""
                        {"affected_source":"agent_transport",
                         "reason":"quota_exceeded",
                         "range_start":"2026-10-30T08:57:00Z",
                         "range_end":"2026-10-30T09:00:00Z"}
                        """));
        return new DeviceEventBatch(1, UUID.randomUUID(), deviceId, List.of(event));
    }

    private static DeviceEventIngestionService transactionalProxy(
            DeviceEventIngestionService target, PlatformTransactionManager manager) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,
                new AnnotationTransactionAttributeSource()));
        return (DeviceEventIngestionService) factory.getProxy();
    }
}

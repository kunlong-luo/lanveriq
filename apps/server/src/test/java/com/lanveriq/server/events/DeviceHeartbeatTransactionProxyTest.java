package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
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

class DeviceHeartbeatTransactionProxyTest {

    private final UUID id = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(id, "c".repeat(64));

    private DeviceHeartbeat heartbeat() {
        return new DeviceHeartbeat(1, id, "2026-10-30T09:00:00Z", "0.1.0", 1,
                "running", Map.of("hardware_inventory", "not_collected"),
                0, 0, null, null, 0, false, List.of());
    }

    @Test
    void completeHeartbeatReceiptIsReturnedAfterCommit() {
        var repository = Mockito.mock(DeviceHeartbeatRepository.class);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = proxy(repository, manager);
        var receipt = proxy.ingest(identity, id, heartbeat());
        assertNotNull(receipt.serverTime());
        assertNotNull(receipt.requestId());
        verify(repository).record(Mockito.eq(identity), Mockito.eq(id), Mockito.any());
        verify(manager).commit(status);
    }

    @Test
    void databaseCredentialFailureRollsBackWithoutHeartbeatReceipt() {
        var repository = Mockito.mock(DeviceHeartbeatRepository.class);
        Mockito.doThrow(new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE))
                .when(repository).record(Mockito.any(), Mockito.any(), Mockito.any());
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = proxy(repository, manager);
        assertThrows(DeviceHeartbeatRejection.class, () ->
                proxy.ingest(identity, id, heartbeat()));
        verify(manager).rollback(status);
    }

    private static DeviceHeartbeatService proxy(DeviceHeartbeatRepository repository,
                                                PlatformTransactionManager manager) {
        var target = new DeviceHeartbeatService(repository, JsonMapper.builder().build());
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,
                new AnnotationTransactionAttributeSource()));
        return (DeviceHeartbeatService) factory.getProxy();
    }
}

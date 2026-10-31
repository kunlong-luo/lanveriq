package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class DeviceActivationServiceTest {
    private final UUID id = UUID.randomUUID();
    private final VerifiedDeviceCertificate identity =
            new VerifiedDeviceCertificate(id, "a".repeat(64));

    private DeviceActivationRequest request(UUID requestId) {
        return new DeviceActivationRequest(1, requestId, "0.1.0");
    }

    @Test
    void validActivationReturnsActiveOnlyAfterTransactionalRepositoryCall() {
        var repository = Mockito.mock(DeviceActivationRepository.class);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(Mockito.any(TransactionDefinition.class))).thenReturn(status);
        var receipt = proxy(repository, manager).activate(identity, id, request(id));

        assertEquals(1, receipt.schemaVersion());
        assertEquals(id, receipt.deviceId());
        assertEquals("active", receipt.enrollmentState());
        assertNotNull(receipt.serverTime());
        assertNotNull(receipt.requestId());
        verify(repository).activate(identity, id);
        verify(manager).commit(status);
    }

    @Test
    void invalidDeviceSchemaAndAgentVersionAreRejectedBeforeRepositoryCall() {
        var repository = Mockito.mock(DeviceActivationRepository.class);
        var service = new DeviceActivationService(repository);
        var wrongPath = UUID.randomUUID();
        for (var rejected : new DeviceActivationRequest[]{
                request(wrongPath),
                new DeviceActivationRequest(2, id, "0.1.0"),
                new DeviceActivationRequest(1, id, "invalid version!"),
                new DeviceActivationRequest(1, id, null),
                new DeviceActivationRequest(1, null, "0.1.0")
        }) {
            var error = assertThrows(DeviceActivationRejection.class,
                    () -> service.activate(identity, id, rejected));
            assertEquals(DeviceActivationRejection.Reason.INVALID_REQUEST, error.reason());
        }
        assertEquals(DeviceActivationRejection.Reason.INVALID_REQUEST,
                assertThrows(DeviceActivationRejection.class, () ->
                        service.activate(identity, wrongPath, request(id))).reason());
        verify(repository, never()).activate(Mockito.any(), Mockito.any());
    }

    @Test
    void identityDenialRollsBackWithoutAnyActivationReceipt() {
        var repository = Mockito.mock(DeviceActivationRepository.class);
        Mockito.doThrow(new DeviceActivationRejection(DeviceActivationRejection.Reason.DENIED))
                .when(repository).activate(identity, id);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(Mockito.any(TransactionDefinition.class))).thenReturn(status);

        var error = assertThrows(DeviceActivationRejection.class,
                () -> proxy(repository, manager).activate(identity, id, request(id)));
        assertEquals(DeviceActivationRejection.Reason.DENIED, error.reason());
        verify(manager).rollback(status);
        verify(manager, never()).commit(status);
    }

    private static DeviceActivationService proxy(DeviceActivationRepository repository,
                                                  PlatformTransactionManager manager) {
        var factory = new ProxyFactory(new DeviceActivationService(repository));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,
                new AnnotationTransactionAttributeSource()));
        return (DeviceActivationService) factory.getProxy();
    }
}

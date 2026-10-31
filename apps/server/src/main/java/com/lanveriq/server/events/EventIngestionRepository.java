package com.lanveriq.server.events;

import java.util.List;

/** Must be called inside the public service's single Spring transaction. */
interface EventIngestionRepository {
    void appendOrVerify(VerifiedDeviceCertificate identity,
                        List<DeviceEventBatchPolicy.CanonicalEvent> events);
}

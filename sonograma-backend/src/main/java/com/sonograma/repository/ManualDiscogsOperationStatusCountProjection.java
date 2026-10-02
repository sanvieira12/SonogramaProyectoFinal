package com.sonograma.repository;

import com.sonograma.enums.ManualDiscogsImportOperationStatus;

public interface ManualDiscogsOperationStatusCountProjection {
    ManualDiscogsImportOperationStatus getStatus();
    long getOperationCount();
}

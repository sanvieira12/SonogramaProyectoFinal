package com.sonograma.repository;

public interface ManualDiscogsSourceCopyAggregateProjection {
    long getProvablePhysicalCopyCount();
    long getAvailableCopyCount();
    long getSoldCopyCount();
    long getRemovedCopyCount();
    long getDistinctReleaseCount();
}

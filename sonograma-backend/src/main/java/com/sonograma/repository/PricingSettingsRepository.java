package com.sonograma.repository;

import com.sonograma.entity.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Long> {
}

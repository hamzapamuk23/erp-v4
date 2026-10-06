package com.smart.erp.spike.s1.sample;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRecordRepository extends JpaRepository<SampleRecord, UUID> {}

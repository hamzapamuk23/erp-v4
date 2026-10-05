package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.List;
import java.util.Map;

/** Outcome of one tenant-walking run: tenants whose publications were resubmitted, and failures with their cause. */
public record RepublishReport(List<TenantKey> republished, Map<TenantKey, String> failed) {}

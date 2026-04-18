/**
 * Feature-flag integration — exposes Unleash flag state to the UI via
 * a cached read-only endpoint. See {@link com.mirador.features.FeatureFlagController}
 * and ADR-0024 for the rationale (no SDK, single cached HTTP call).
 */
package com.mirador.features;

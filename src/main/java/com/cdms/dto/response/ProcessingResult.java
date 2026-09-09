package com.cdms.dto.response;

/**
 * Processing outcome for a single change event.
 *
 * <p>Used as the return type of {@code ChangeProcessingService.processChange()}.
 * All three ingestion sources (Webhook, Scheduler, Excel) share this enum.
 *
 * <ul>
 *   <li>{@code PROCESSED} — event was new, product updated successfully.</li>
 *   <li>{@code DUPLICATE} — event_id already exists in DB; ignored.</li>
 *   <li>{@code OLD_DATA}  — incoming version ≤ stored version; ignored.</li>
 *   <li>{@code FAILED}    — unexpected error during processing.</li>
 * </ul>
 */
public enum ProcessingResult {
    PROCESSED,
    DUPLICATE,
    OLD_DATA,
    FAILED
}

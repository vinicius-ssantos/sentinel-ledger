package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.ledger.PostedLedgerEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record LedgerEntryResponse(

	@Schema(example = "capture:2f9a1e2c-8b1e-4c2a-9a0e-2c8b1e4c2a9a:idem-key-001")
	String businessEffectReference,

	@Schema(example = "DEBIT")
	String direction,

	@Schema(example = "4000")
	String amountInMinorUnits,

	@Schema(example = "BRL")
	String currency,

	Instant postedAt
) {

	public static LedgerEntryResponse from(PostedLedgerEntry entry) {
		return new LedgerEntryResponse(
			entry.businessEffectReference(),
			entry.direction().name(),
			entry.amount().amountInMinorUnitsText(),
			entry.amount().currency().code(),
			entry.postedAt()
		);
	}
}

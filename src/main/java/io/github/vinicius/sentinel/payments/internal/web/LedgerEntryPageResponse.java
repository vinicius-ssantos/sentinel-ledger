package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.ledger.LedgerEntryPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LedgerEntryPageResponse(

	List<LedgerEntryResponse> entries,

	@Schema(description = "Opaque token for the next page, or null if this is the last page.", nullable = true)
	String nextCursor
) {

	public static LedgerEntryPageResponse from(LedgerEntryPage page) {
		return new LedgerEntryPageResponse(page.entries().stream().map(LedgerEntryResponse::from).toList(), page.nextCursor());
	}
}

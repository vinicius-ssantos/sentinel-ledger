package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.LedgerEntryPage;
import io.github.vinicius.sentinel.ledger.LedgerEntryQueryPort;
import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lives in {@code payments}, not {@code ledger}: browsing a ledger account by merchant ownership needs both the
 * {@code ledger} and {@code merchant} module APIs, and only {@code payments} is allowed to depend on both per
 * docs/ARCHITECTURE.md's module table. The {@code ledger} module itself stays merchant-agnostic. The MVP only
 * exposes each merchant's own {@code merchant-payable} account; the shared {@code psp-clearing-receivable}
 * account is an internal/operator concern reserved for #24's reconciliation tooling, not merchant self-service.
 */
@RestController
@RequestMapping("/api/v1/ledger/accounts")
@SecurityRequirement(name = "merchantBasicAuth")
class LedgerAccountEntriesController {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 200;

	private final CurrentMerchantResolver currentMerchantResolver;
	private final LedgerEntryQueryPort ledgerEntryQueryPort;

	LedgerAccountEntriesController(CurrentMerchantResolver currentMerchantResolver, LedgerEntryQueryPort ledgerEntryQueryPort) {
		this.currentMerchantResolver = currentMerchantResolver;
		this.ledgerEntryQueryPort = ledgerEntryQueryPort;
	}

	@GetMapping("/{accountId}/entries")
	@Operation(
		summary = "Browse a ledger account's entries",
		description = "Cursor-paginated, oldest first. Only the authenticated merchant's own merchant-payable "
			+ "account is browsable in the MVP."
	)
	@ApiResponse(responseCode = "200", description = "A page of entries")
	@ApiResponse(responseCode = "400", description = "The cursor parameter is not a value this API previously returned")
	@ApiResponse(responseCode = "404", description = "No such account for the authenticated merchant")
	LedgerEntryPageResponse entries(
		@PathVariable String accountId,
		@Parameter(description = "Opaque token from a previous response's nextCursor. Omit for the first page.")
		@RequestParam(required = false) String cursor,
		@Parameter(description = "Maximum entries to return, 1-200.")
		@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit
	) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		AccountId ownAccount = AccountId.merchantPayable(merchantId.value(), Currency.BRL);
		if (!ownAccount.value().equals(accountId)) {
			throw new LedgerAccountNotFoundException();
		}

		int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
		try {
			LedgerEntryPage page = ledgerEntryQueryPort.findByAccount(ownAccount, cursor, boundedLimit);
			return LedgerEntryPageResponse.from(page);
		} catch (IllegalArgumentException e) {
			throw new InvalidLedgerCursorException();
		}
	}
}

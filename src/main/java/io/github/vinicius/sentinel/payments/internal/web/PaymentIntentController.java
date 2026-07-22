package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.payments.internal.RefundPaymentIntentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-intents")
@SecurityRequirement(name = "merchantBasicAuth")
class PaymentIntentController {

	private static final URI COLLECTION_URI = URI.create("/api/v1/payment-intents");

	private final PaymentIntentStore paymentIntentStore;
	private final CurrentMerchantResolver currentMerchantResolver;
	private final PaymentIntentCommandService paymentIntentCommandService;
	private final AuthorizePaymentIntentService authorizePaymentIntentService;
	private final CapturePaymentIntentService capturePaymentIntentService;
	private final RefundPaymentIntentService refundPaymentIntentService;

	PaymentIntentController(
		PaymentIntentStore paymentIntentStore,
		CurrentMerchantResolver currentMerchantResolver,
		PaymentIntentCommandService paymentIntentCommandService,
		AuthorizePaymentIntentService authorizePaymentIntentService,
		CapturePaymentIntentService capturePaymentIntentService,
		RefundPaymentIntentService refundPaymentIntentService
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.currentMerchantResolver = currentMerchantResolver;
		this.paymentIntentCommandService = paymentIntentCommandService;
		this.authorizePaymentIntentService = authorizePaymentIntentService;
		this.capturePaymentIntentService = capturePaymentIntentService;
		this.refundPaymentIntentService = refundPaymentIntentService;
	}

	@PostMapping
	@Operation(
		summary = "Create a payment intent",
		description = "Creates a payment intent owned by the authenticated merchant in the CREATED state. "
			+ "Requires an Idempotency-Key header; replaying the same key and body returns the original result."
	)
	@ApiResponse(responseCode = "201", description = "Payment intent created")
	@ApiResponse(responseCode = "400", description = "Invalid amount, malformed request, or missing/invalid idempotency key")
	@ApiResponse(responseCode = "409", description = "Idempotency key reused for a different request, or still in progress")
	@ApiResponse(responseCode = "422", description = "Unsupported currency")
	ResponseEntity<String> create(
		@Parameter(description = "16-128 visible ASCII characters, unique per merchant and operation", required = true)
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
		@Valid @RequestBody CreatePaymentIntentRequest request
	) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		IdempotencyKey idempotencyKey = requireIdempotencyKey(idempotencyKeyHeader);
		ApiResult result = paymentIntentCommandService.create(
			merchantId, idempotencyKey, request.amountInMinorUnits(), request.currency(), COLLECTION_URI
		);
		return toResponseEntity(result);
	}

	@PostMapping("/{id}/authorize")
	@Operation(
		summary = "Authorize a payment intent",
		description = "Requests authorization from the simulated PSP outside any database transaction. "
			+ "May return 202 with AUTHORIZATION_UNKNOWN when the provider outcome is not yet known; "
			+ "retrying with the same Idempotency-Key recovers through provider status lookup rather than "
			+ "repeating the call."
	)
	@ApiResponse(responseCode = "200", description = "Authorization reached a final state (AUTHORIZED, DECLINED, or FAILED)")
	@ApiResponse(responseCode = "202", description = "Authorization outcome is not yet known (AUTHORIZATION_UNKNOWN)")
	@ApiResponse(responseCode = "400", description = "Missing or invalid idempotency key")
	@ApiResponse(responseCode = "404", description = "Payment intent absent or owned by another merchant")
	@ApiResponse(responseCode = "409", description = "Invalid transition, idempotency key reused, or still in progress")
	ResponseEntity<String> authorize(
		@PathVariable UUID id,
		@Parameter(description = "16-128 visible ASCII characters, unique per merchant and operation", required = true)
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader
	) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		IdempotencyKey idempotencyKey = requireIdempotencyKey(idempotencyKeyHeader);
		URI instance = URI.create("/api/v1/payment-intents/" + id + "/authorize");
		ApiResult result = authorizePaymentIntentService.authorize(merchantId, idempotencyKey, new PaymentIntentId(id), instance);
		return toResponseEntity(result);
	}

	@PostMapping("/{id}/captures")
	@Operation(
		summary = "Capture an authorized payment intent",
		description = "Captures all or part of the remaining authorized amount. Never calls the PSP; the captured "
			+ "amount and its balanced ledger transaction are persisted in one local transaction."
	)
	@ApiResponse(responseCode = "200", description = "Capture applied (state is PARTIALLY_CAPTURED or CAPTURED)")
	@ApiResponse(responseCode = "400", description = "Invalid amount, malformed request, or missing/invalid idempotency key")
	@ApiResponse(responseCode = "404", description = "Payment intent absent or owned by another merchant")
	@ApiResponse(responseCode = "409", description = "Capture exceeds remaining authorization, invalid transition, "
		+ "idempotency key reused, still in progress, or lost a concurrent race")
	@ApiResponse(responseCode = "422", description = "Unsupported currency")
	ResponseEntity<String> capture(
		@PathVariable UUID id,
		@Parameter(description = "16-128 visible ASCII characters, unique per merchant and operation", required = true)
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
		@Valid @RequestBody CaptureRequest request
	) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		IdempotencyKey idempotencyKey = requireIdempotencyKey(idempotencyKeyHeader);
		URI instance = URI.create("/api/v1/payment-intents/" + id + "/captures");
		ApiResult result = capturePaymentIntentService.capture(
			merchantId, idempotencyKey, new PaymentIntentId(id), request.amountInMinorUnits(), request.currency(), instance
		);
		return toResponseEntity(result);
	}

	@PostMapping("/{id}/refunds")
	@Operation(
		summary = "Refund a captured payment intent",
		description = "Refunds all or part of the remaining refundable (captured minus already refunded) amount. "
			+ "Never calls the PSP; the refunded amount and its compensating ledger transaction are persisted in "
			+ "one local transaction. The original capture entries are never modified."
	)
	@ApiResponse(responseCode = "200", description = "Refund applied (state is PARTIALLY_REFUNDED or REFUNDED)")
	@ApiResponse(responseCode = "400", description = "Invalid amount, malformed request, or missing/invalid idempotency key")
	@ApiResponse(responseCode = "404", description = "Payment intent absent or owned by another merchant")
	@ApiResponse(responseCode = "409", description = "Refund exceeds the remaining refundable amount, invalid transition, "
		+ "idempotency key reused, still in progress, or lost a concurrent race")
	@ApiResponse(responseCode = "422", description = "Unsupported currency")
	ResponseEntity<String> refund(
		@PathVariable UUID id,
		@Parameter(description = "16-128 visible ASCII characters, unique per merchant and operation", required = true)
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
		@Valid @RequestBody RefundRequest request
	) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		IdempotencyKey idempotencyKey = requireIdempotencyKey(idempotencyKeyHeader);
		URI instance = URI.create("/api/v1/payment-intents/" + id + "/refunds");
		ApiResult result = refundPaymentIntentService.refund(
			merchantId, idempotencyKey, new PaymentIntentId(id), request.amountInMinorUnits(), request.currency(), instance
		);
		return toResponseEntity(result);
	}

	@GetMapping("/{id}")
	@Operation(
		summary = "Read a payment intent",
		description = "Returns the current state of a payment intent owned by the authenticated merchant."
	)
	@ApiResponse(responseCode = "200", description = "Payment intent found")
	@ApiResponse(responseCode = "404", description = "Payment intent absent or owned by another merchant")
	PaymentIntentResponse get(@PathVariable UUID id) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		return paymentIntentStore.findOwned(new PaymentIntentId(id), merchantId)
			.map(PaymentIntentResponse::from)
			.orElseThrow(PaymentIntentNotFoundException::new);
	}

	private static IdempotencyKey requireIdempotencyKey(String header) {
		if (header == null || header.isBlank()) {
			throw new MissingIdempotencyKeyException();
		}
		try {
			return new IdempotencyKey(header);
		} catch (IllegalArgumentException e) {
			throw new InvalidIdempotencyKeyException();
		}
	}

	private static ResponseEntity<String> toResponseEntity(ApiResult result) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status())
			.contentType(MediaType.parseMediaType(result.contentType()));
		if (result.location() != null) {
			builder.location(URI.create(result.location()));
		}
		if (result.replayed()) {
			builder.header("Idempotent-Replayed", "true");
		}
		if (result.retryAfterSeconds() != null) {
			builder.header("Retry-After", String.valueOf(result.retryAfterSeconds()));
		}
		return builder.body(result.body());
	}
}

package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
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

	PaymentIntentController(
		PaymentIntentStore paymentIntentStore,
		CurrentMerchantResolver currentMerchantResolver,
		PaymentIntentCommandService paymentIntentCommandService
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.currentMerchantResolver = currentMerchantResolver;
		this.paymentIntentCommandService = paymentIntentCommandService;
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

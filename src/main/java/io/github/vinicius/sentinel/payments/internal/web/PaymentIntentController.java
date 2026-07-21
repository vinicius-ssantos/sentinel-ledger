package io.github.vinicius.sentinel.payments.internal.web;

import io.github.vinicius.sentinel.merchant.CurrentMerchantResolver;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-intents")
@SecurityRequirement(name = "merchantBasicAuth")
class PaymentIntentController {

	private final PaymentIntentStore paymentIntentStore;
	private final CurrentMerchantResolver currentMerchantResolver;

	PaymentIntentController(PaymentIntentStore paymentIntentStore, CurrentMerchantResolver currentMerchantResolver) {
		this.paymentIntentStore = paymentIntentStore;
		this.currentMerchantResolver = currentMerchantResolver;
	}

	@PostMapping
	@Operation(
		summary = "Create a payment intent",
		description = "Creates a payment intent owned by the authenticated merchant in the CREATED state."
	)
	@ApiResponse(responseCode = "201", description = "Payment intent created")
	@ApiResponse(responseCode = "400", description = "Invalid amount or malformed request")
	@ApiResponse(responseCode = "422", description = "Unsupported currency")
	ResponseEntity<PaymentIntentResponse> create(@Valid @RequestBody CreatePaymentIntentRequest request) {
		MerchantId merchantId = currentMerchantResolver.requireCurrentMerchantId();
		Currency currency = resolveCurrency(request.currency());
		Money amount = toPositiveMoney(parseAmount(request.amountInMinorUnits()), currency);

		PaymentIntent paymentIntent = PaymentIntent.create(
			PaymentIntentId.generate(),
			merchantId,
			amount,
			Instant.now()
		);
		paymentIntentStore.save(paymentIntent);

		URI location = URI.create("/api/v1/payment-intents/" + paymentIntent.id().value());
		return ResponseEntity.created(location).body(PaymentIntentResponse.from(paymentIntent));
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

	private static Currency resolveCurrency(String currencyCode) {
		if (!Currency.BRL.code().equalsIgnoreCase(currencyCode)) {
			throw new UnsupportedCurrencyException(currencyCode);
		}
		return Currency.BRL;
	}

	private static long parseAmount(String amountInMinorUnits) {
		try {
			return Long.parseLong(amountInMinorUnits);
		} catch (NumberFormatException e) {
			throw new InvalidPaymentIntentAmountException("amountInMinorUnits must be a valid integer");
		}
	}

	private static Money toPositiveMoney(long amountInMinorUnits, Currency currency) {
		try {
			return Money.positive(amountInMinorUnits, currency);
		} catch (IllegalArgumentException e) {
			throw new InvalidPaymentIntentAmountException(e.getMessage());
		}
	}
}

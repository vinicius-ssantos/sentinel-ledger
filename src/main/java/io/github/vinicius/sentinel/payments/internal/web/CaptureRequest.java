package io.github.vinicius.sentinel.payments.internal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record CaptureRequest(

	@NotBlank
	@Pattern(regexp = "[0-9]{1,19}", message = "must be a positive integer string of minor currency units")
	@Schema(description = "Positive integer amount to capture, in minor currency units. May be less than the remaining authorized amount for a partial capture.", example = "4000")
	String amountInMinorUnits,

	@NotBlank
	@Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter ISO-style currency code")
	@Schema(description = "Must match the payment intent's currency. MVP accepts BRL only.", example = "BRL")
	String currency
) {}

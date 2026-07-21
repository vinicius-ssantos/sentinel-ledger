package io.github.vinicius.sentinel.payments.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = PaymentIntentController.class)
class PaymentIntentApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleInvalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
		ProblemDetail problem = problem(
			HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
			"Invalid payment intent request", "The request body failed validation.", request
		);
		problem.setProperty("violations", ex.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.toList());
		return respond(problem);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleMalformedRequest(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
			"Invalid payment intent request", "The request body could not be parsed.", request
		));
	}

	@ExceptionHandler(MissingIdempotencyKeyException.class)
	ResponseEntity<ProblemDetail> handleMissingIdempotencyKey(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.BAD_REQUEST, "idempotency-key-required", "IDEMPOTENCY_KEY_REQUIRED",
			"Idempotency key required", "This mutating command requires an Idempotency-Key header.", request
		));
	}

	@ExceptionHandler(InvalidIdempotencyKeyException.class)
	ResponseEntity<ProblemDetail> handleInvalidIdempotencyKey(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.BAD_REQUEST, "idempotency-key-invalid", "IDEMPOTENCY_KEY_INVALID",
			"Idempotency key invalid", "The Idempotency-Key header must be 16-128 visible ASCII characters.", request
		));
	}

	@ExceptionHandler(PaymentIntentNotFoundException.class)
	ResponseEntity<ProblemDetail> handleNotFound(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.NOT_FOUND, "payment-intent-not-found", "PAYMENT_INTENT_NOT_FOUND",
			"Payment intent not found",
			"No payment intent exists for the authenticated merchant with this identifier.",
			request
		));
	}

	private static ProblemDetail problem(
		HttpStatus status, String typeSlug, String code, String title, String detail, HttpServletRequest request
	) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://sentinel-ledger.dev/problems/" + typeSlug));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		return problem;
	}

	private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus())
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem);
	}
}

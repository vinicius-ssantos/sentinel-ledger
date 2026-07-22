package io.github.vinicius.sentinel.reconciliation.internal.web;

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

@RestControllerAdvice(assignableTypes = ReconciliationController.class)
class ReconciliationApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handleInvalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
		ProblemDetail problem = problem(
			HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
			"Invalid reconciliation request", "The request body failed validation.", request
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
			"Invalid reconciliation request", "The request body could not be parsed.", request
		));
	}

	@ExceptionHandler(ReconciliationCaseNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCaseNotFound(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.NOT_FOUND, "reconciliation-case-not-found", "RECONCILIATION_CASE_NOT_FOUND",
			"Reconciliation case not found", "No reconciliation case exists with this identifier.", request
		));
	}

	@ExceptionHandler(ReconciliationCaseAlreadyResolvedException.class)
	ResponseEntity<ProblemDetail> handleAlreadyResolved(ReconciliationCaseAlreadyResolvedException ex, HttpServletRequest request) {
		return respond(problem(
			HttpStatus.CONFLICT, "reconciliation-case-already-resolved", "RECONCILIATION_CASE_ALREADY_RESOLVED",
			"Reconciliation case already resolved", ex.getMessage(), request
		));
	}

	@ExceptionHandler(PaymentIntentNotFoundException.class)
	ResponseEntity<ProblemDetail> handlePaymentIntentNotFound(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.NOT_FOUND, "payment-intent-not-found", "PAYMENT_INTENT_NOT_FOUND",
			"Payment intent not found", "No payment intent exists with this identifier.", request
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

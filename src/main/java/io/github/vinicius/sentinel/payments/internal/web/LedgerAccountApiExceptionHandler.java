package io.github.vinicius.sentinel.payments.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = LedgerAccountEntriesController.class)
class LedgerAccountApiExceptionHandler {

	@ExceptionHandler(LedgerAccountNotFoundException.class)
	ResponseEntity<ProblemDetail> handleNotFound(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.NOT_FOUND, "ledger-account-not-found", "LEDGER_ACCOUNT_NOT_FOUND",
			"Ledger account not found",
			"No ledger account exists for the authenticated merchant with this identifier.",
			request
		));
	}

	@ExceptionHandler(InvalidLedgerCursorException.class)
	ResponseEntity<ProblemDetail> handleInvalidCursor(HttpServletRequest request) {
		return respond(problem(
			HttpStatus.BAD_REQUEST, "invalid-ledger-cursor", "INVALID_LEDGER_CURSOR",
			"Invalid cursor", "The cursor query parameter is not a value this API previously returned.", request
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

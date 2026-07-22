package io.github.vinicius.sentinel.reconciliation.internal.web;

final class ReconciliationCaseAlreadyResolvedException extends RuntimeException {

	ReconciliationCaseAlreadyResolvedException(String detail) {
		super(detail);
	}
}

package io.github.vinicius.sentinel.reconciliation.internal.web;

final class ReconciliationCaseNotFoundException extends RuntimeException {

	ReconciliationCaseNotFoundException() {
		super("no reconciliation case exists with the given identifier");
	}
}

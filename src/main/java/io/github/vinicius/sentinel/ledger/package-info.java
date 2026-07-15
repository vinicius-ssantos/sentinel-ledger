/**
 * Owns ledger accounts, transactions, entries, and rebuildable projections.
 */
@org.springframework.modulith.ApplicationModule(
	id = "ledger",
	displayName = "Ledger",
	allowedDependencies = "audit"
)
package io.github.vinicius.sentinel.ledger;

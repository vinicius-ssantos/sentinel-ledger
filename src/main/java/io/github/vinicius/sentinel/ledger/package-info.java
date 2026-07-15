/**
 * Owns ledger accounts, transactions, entries, and rebuildable projections.
 */
@org.springframework.modulith.ApplicationModule(
	id = "ledger",
	displayName = "Ledger",
	allowedDependencies = { "money", "audit" }
)
package io.github.vinicius.sentinel.ledger;

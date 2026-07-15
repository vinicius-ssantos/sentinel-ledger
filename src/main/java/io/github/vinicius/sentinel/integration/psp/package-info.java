/**
 * Owns the provider-neutral PSP contract and simulated provider adapter.
 */
@org.springframework.modulith.ApplicationModule(
	id = "integration.psp",
	displayName = "PSP Integration",
	allowedDependencies = "payments"
)
package io.github.vinicius.sentinel.integration.psp;

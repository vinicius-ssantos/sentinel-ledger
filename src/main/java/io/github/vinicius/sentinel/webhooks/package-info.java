/**
 * Owns signed webhook delivery to the merchant's registered endpoint, its retry/delivery history, and the
 * signature-verification algorithm a receiver uses to authenticate a callback.
 */
@org.springframework.modulith.ApplicationModule(
	id = "webhooks",
	displayName = "Webhooks",
	allowedDependencies = {}
)
package io.github.vinicius.sentinel.webhooks;

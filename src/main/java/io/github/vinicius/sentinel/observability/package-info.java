/**
 * Owns cross-cutting telemetry configuration: correlation propagation and structured logging. Business metrics are
 * recorded by each owning module directly against Micrometer's {@code MeterRegistry} (a shared technical
 * primitive, not a module dependency), the same way every module already uses {@code ObjectMapper} or
 * {@code JdbcClient} without this module being involved -- so this module does not need to depend on any domain
 * module today. Widen {@code allowedDependencies} only when a concrete cross-cutting need (for example, consuming
 * a domain module's public events) actually exists.
 */
@org.springframework.modulith.ApplicationModule(
	id = "observability",
	displayName = "Observability",
	allowedDependencies = {}
)
package io.github.vinicius.sentinel.observability;

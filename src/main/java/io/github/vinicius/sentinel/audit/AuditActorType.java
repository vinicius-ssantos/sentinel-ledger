package io.github.vinicius.sentinel.audit;

/**
 * Operator and reconciliation-case actors are anticipated by {@code docs/ARCHITECTURE.md}'s module dependency
 * table but have no caller yet; only {@code MERCHANT} is emitted until an operator identity exists.
 */
public enum AuditActorType {
	MERCHANT,
	OPERATOR,
	SYSTEM
}

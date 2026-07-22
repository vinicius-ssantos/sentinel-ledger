package io.github.vinicius.sentinel.reconciliation;

public interface CurrentOperatorResolver {

	/**
	 * Resolves the operator identity of the authenticated principal.
	 *
	 * @throws IllegalStateException when no authenticated operator principal is available
	 */
	OperatorId requireCurrentOperatorId();
}

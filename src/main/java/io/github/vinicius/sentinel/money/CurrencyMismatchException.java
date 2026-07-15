package io.github.vinicius.sentinel.money;

public final class CurrencyMismatchException extends IllegalArgumentException {

	private final Currency left;
	private final Currency right;

	public CurrencyMismatchException(Currency left, Currency right) {
		super("money currencies must match: " + left.code() + " != " + right.code());
		this.left = left;
		this.right = right;
	}

	public Currency left() {
		return left;
	}

	public Currency right() {
		return right;
	}
}

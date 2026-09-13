package fr.lacaleche.glue.mcsx.client.style.internal;

import fr.lacaleche.glue.mcsx.client.theme.Token;

public sealed interface StyleValue permits StyleValue.Literal, StyleValue.Percent,
        StyleValue.Scalar, StyleValue.Keyword, StyleValue.TokenReference {

    record Literal(int value) implements StyleValue {
    }

    record Percent(float value) implements StyleValue {
    }

    record Scalar(float value) implements StyleValue {
    }

    record Keyword(String value) implements StyleValue {
    }

    record TokenReference(Token<Integer> token) implements StyleValue {
    }
}

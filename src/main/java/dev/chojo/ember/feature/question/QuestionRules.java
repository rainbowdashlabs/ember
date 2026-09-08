/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a question allows beyond its kind, typed per kind rather than flattened into one record with
 * every setting of every kind in it.
 *
 * <p>A form question and a board field are each modelled this way already, and the six that ask with
 * a flat settings record are the ones this brings into line: the options of a choice mean nothing on
 * a date, and a bound means nothing on a line of text.
 */
public sealed interface QuestionRules {

    /** A kind with nothing further to say about what it accepts. */
    record None() implements QuestionRules {}

    /**
     * The answers a choice allows.
     *
     * @param options what may be answered; an empty list allows anything, which is what a choice
     *                nobody has written options for has always done
     */
    record Choice(List<String> options) implements QuestionRules {
        public Choice {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * What a number has to sit between.
     *
     * <p>Held as a decimal because one of the features that has bounds measures gear: a length is
     * bounded at 2.5 metres as readily as a count of guests is bounded at five.
     *
     * @param min the smallest allowed answer, or null where there is none
     * @param max the largest allowed answer, or null where there is none
     */
    record Bounds(BigDecimal min, BigDecimal max) implements QuestionRules {}

    /** The rules of a kind that has none. */
    static QuestionRules none() {
        return new None();
    }

    /** The rules of a choice, from whatever the feature stored. */
    static QuestionRules choice(List<String> options) {
        return new Choice(options);
    }

    /** The rules of a number, from whatever the feature stored. */
    static QuestionRules bounds(BigDecimal min, BigDecimal max) {
        return new Bounds(min, max);
    }

    /** The rules of a number whose bounds are whole, which is how most features write them down. */
    static QuestionRules bounds(Integer min, Integer max) {
        return new Bounds(min == null ? null : BigDecimal.valueOf(min), max == null ? null : BigDecimal.valueOf(max));
    }
}

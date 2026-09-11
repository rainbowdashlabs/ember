/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reading a member's name out of a subject line somebody typed.
 *
 * <p>Whoever scans a document types a name into the subject and the rule reads it. That is the only way a
 * document reaches a member at all, and it is a guess about a line a human wrote, which is why a rule has
 * to ask for it.
 *
 * <p><b>An ambiguous subject binds nobody.</b> Two members the subject fits equally well, or a subject
 * that reads like two different people, files the document under nobody with the reason recorded. A
 * document on the wrong member is worse than a document on none, and a hidden document on the wrong
 * member is worse still: it would be invisible to the person it actually concerns and visible to somebody
 * it does not.
 *
 * <p><b>Two tokens are the bar.</b> A name is read as the words it is made of, so leaving out a middle
 * name or half of a double surname still finds somebody, which is what whoever types a subject line
 * actually does. Two of those words have to be there, or all of them where the name is a single word, so
 * a bare surname binds nobody however unique it is in the station.
 *
 * <p><b>Spelling is folded.</b> Case is ignored, and umlauts are reduced both ways so that Mueller,
 * Müller and Muller are one word. The reduction can merge two genuinely different names, Bauer and Bär
 * among them, and that is acceptable because of how it fails: a collision is an ambiguity, an ambiguity
 * binds nobody, and so it costs a miss rather than a wrong binding.
 *
 * <p>Filing under nobody is the ordinary outcome of this feature rather than a failure, so declining to
 * guess costs nothing that has to be recovered.
 */
public final class SubjectMemberMatch {

    private static final Pattern NAME_SEPARATOR = Pattern.compile("[\\s-]+");
    private static final Pattern SUBJECT_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int SHORTEST_TOKEN = 2;
    private static final int TOKENS_WANTED = 2;

    /**
     * A member as this matcher needs to see them: an identifier and the name to look for.
     *
     * @param memberId the member
     * @param fullName their name as the station holds it
     */
    public record Candidate(int memberId, String fullName) {}

    private SubjectMemberMatch() {}

    /**
     * The one member this subject names, or nothing where it names none or more than one.
     *
     * <p>Whole words are compared and never parts of them, so a subject reading "Anastasia Weber" does
     * not reach a member called Ana Weber. Of the members who clear the bar, the one whose name the
     * subject says the most of wins, and where two are said equally often the one named more completely
     * wins, which is what tells Anna Weber from Anna Weber-Schmidt. Still level binds nobody.
     *
     * @param subject    what the message was called
     * @param candidates the station's members
     * @return the member the subject names
     */
    public static Optional<Candidate> soleMatch(String subject, List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        Set<String> spoken = tokens(subject, SUBJECT_SEPARATOR);
        if (spoken.isEmpty()) return Optional.empty();

        List<Named> named = candidates.stream()
                .map(candidate -> score(candidate, spoken))
                .flatMap(Optional::stream)
                .toList();
        if (named.isEmpty()) return Optional.empty();

        Named best = named.stream().max(BY_HOW_WELL_IT_FITS).orElseThrow();
        boolean level = named.stream()
                .anyMatch(other ->
                        other.candidate().memberId() != best.candidate().memberId()
                                && BY_HOW_WELL_IT_FITS.compare(other, best) == 0);
        return level ? Optional.empty() : Optional.of(best.candidate());
    }

    /**
     * One member measured against a subject: how much of their name it says, and how much it leaves out.
     *
     * @param candidate the member being measured
     * @param hits      the words of the name the subject carries
     * @param unnamed   the words of the name it does not
     */
    private record Named(Candidate candidate, int hits, int unnamed) {}

    private static final Comparator<Named> BY_HOW_WELL_IT_FITS = Comparator.<Named>comparingInt(Named::hits)
            .thenComparing(Comparator.<Named>comparingInt(Named::unnamed).reversed());

    /**
     * What the subject says of one member's name, or nothing where it says too little of it.
     *
     * <p>The bar is two words, and every word where the name has only one. A member with no name worth
     * the term is never guessed at.
     */
    private static Optional<Named> score(Candidate candidate, Set<String> spoken) {
        Set<String> name = tokens(candidate.fullName(), NAME_SEPARATOR);
        if (name.isEmpty()) return Optional.empty();
        int hits = (int) name.stream().filter(spoken::contains).count();
        if (hits < Math.min(name.size(), TOKENS_WANTED)) return Optional.empty();
        return Optional.of(new Named(candidate, hits, name.size() - hits));
    }

    private static Set<String> tokens(String text, Pattern separator) {
        if (text == null || text.isBlank()) return Set.of();
        return separator
                .splitAsStream(folded(text))
                .filter(token -> token.length() >= SHORTEST_TOKEN)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The one spelling every spelling of a word reduces to.
     *
     * <p>Both directions are folded on purpose: the umlaut goes to its bare letter and the two letter
     * form goes to the same one, so Müller, Mueller and Muller are the same word whichever of them the
     * station holds and whichever of them the subject was typed with.
     */
    private static String folded(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replace('ä', 'a')
                .replace('ö', 'o')
                .replace('ü', 'u')
                .replace("ae", "a")
                .replace("oe", "o")
                .replace("ue", "u");
    }
}

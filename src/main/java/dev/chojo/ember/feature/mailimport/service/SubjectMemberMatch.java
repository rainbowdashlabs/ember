/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reading a member's name out of a subject line somebody typed.
 *
 * <p>Whoever scans a document types a name into the subject and the rule reads it. That is the whole
 * mechanism, and it is a guess about a line a human wrote, which is why a rule has to ask for it.
 *
 * <p><b>An ambiguous subject binds nobody.</b> Two members sharing a surname, or a subject that reads
 * like two different people, files the document under nobody with the reason recorded. A document on the
 * wrong member is worse than a document on none, and a hidden document on the wrong member is worse
 * still: it would be invisible to the person it actually concerns and visible to somebody it does not.
 *
 * <p>Filing under nobody is the ordinary outcome of this feature rather than a failure, so declining to
 * guess costs nothing that has to be recovered.
 */
public final class SubjectMemberMatch {

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
     * <p>A name counts as named when every word of it appears in the subject, so "Bescheinigung Anna
     * Weber 2026" finds Anna Weber and "Weber" alone finds nobody where two Webers are members. Where
     * one candidate's name is wholly contained in another's, the longer one is the answer rather than an
     * ambiguity: "Anna Weber" and "Anna Weber-Schmidt" are told apart by the subject naming one of them
     * completely and the other not.
     *
     * @param subject    what the message was called
     * @param candidates the station's members
     * @return the member the subject names
     */
    public static Optional<Candidate> soleMatch(String subject, List<Candidate> candidates) {
        if (subject == null || subject.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String haystack = subject.toLowerCase(Locale.ROOT);
        List<Candidate> named = candidates.stream()
                .filter(candidate -> names(haystack, candidate.fullName()))
                .toList();
        if (named.isEmpty()) return Optional.empty();
        if (named.size() == 1) return Optional.of(named.getFirst());
        return onlyTheLongest(named);
    }

    /**
     * Where several members are named, the one whose name none of the others contains.
     *
     * <p>This is the difference between a genuine ambiguity and a name that happens to sit inside a
     * longer one. Two Webers in a subject saying "Weber" is ambiguous and binds nobody; "Anna
     * Weber-Schmidt" naming both Anna Weber-Schmidt and Anna Weber is not, because one of the two is
     * named more completely than the other.
     */
    private static Optional<Candidate> onlyTheLongest(List<Candidate> named) {
        Candidate longest = named.stream()
                .max((left, right) -> Integer.compare(length(left), length(right)))
                .orElseThrow();
        boolean tied = named.stream()
                .anyMatch(candidate ->
                        candidate.memberId() != longest.memberId() && length(candidate) == length(longest));
        if (tied) return Optional.empty();
        boolean allInside = named.stream()
                .allMatch(candidate -> candidate.memberId() == longest.memberId()
                        || normalise(longest.fullName()).contains(normalise(candidate.fullName())));
        return allInside ? Optional.of(longest) : Optional.empty();
    }

    private static int length(Candidate candidate) {
        return normalise(candidate.fullName()).length();
    }

    private static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean names(String subject, String fullName) {
        String normalised = normalise(fullName);
        if (normalised.isBlank()) return false;
        String[] words = normalised.split(" ");
        for (String word : words) {
            if (word.length() < 2) continue;
            if (!subject.contains(word)) return false;
        }
        return true;
    }
}

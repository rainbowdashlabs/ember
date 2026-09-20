/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

/**
 * The words a document's name is built from, in the two languages documents are written in.
 *
 * <p>They are held here rather than in a template because a name is decided before anything is
 * rendered, and because an archive and a spreadsheet have no template to hold them. Written as an
 * enum rather than looked up by key so that a word that does not exist cannot be asked for.
 *
 * <p>Each word is what the interface already calls the thing. A name invented for a file would be a
 * second vocabulary, and a reader would have to learn which of the two the download folder speaks.
 */
public enum DocumentWord {

    ATTENDANCE("Anwesenheit", "Attendance"),
    ATTENDANCE_SHEET("Anwesenheitsliste", "Attendance Sheet"),
    EVENTS("Termine", "Events"),
    REGISTRATIONS("Anmeldungen", "Registrations"),
    MEMBERS("Mitglieder", "Members"),
    CLUSTER_MEMBERS("Verbandsmitglieder", "Association Members"),
    MOVEMENTS("Bewegungen", "Movements"),
    MEMBER_INVENTORY("Mitglieder Inventar", "Member Inventory"),
    CHECKLIST("Checkliste", "Checklist"),
    QUESTIONS("Fragen", "Questions"),
    ANSWERS("Lösungen", "Answers"),
    PROTOCOL("Protokoll", "Protocol"),
    EVALUATION("Auswertung", "Evaluation"),
    FORM_ANSWERS("Antworten", "Answers"),
    DATA_EXPORT("Datenauskunft", "Data Export"),
    ISSUE("Ausgabe", "Issue"),
    RETURN("Rückgabe", "Return"),
    EXCHANGE("Tausch", "Exchange"),
    REQUEST("Anfrage", "Request"),
    WEEK("KW", "Week"),
    TRIAL("Probe", "Trial"),
    MEMBER("Mitglied", "Member"),
    GUARDIAN("Erziehungsberechtigter", "Guardian"),
    TEAM("Team", "Team"),
    MANAGER("Manager", "Manager");

    /**
     * What a kind of member is called, for a name built around one.
     *
     * <p>Returns the token itself for anything unrecognised, which is better in a filename than
     * nothing at all and says plainly that a kind has been added without being given a word.
     */
    public static String forUserType(String userType, String language) {
        return switch (userType == null ? "" : userType) {
            case "TRIAL" -> TRIAL.in(language);
            case "MEMBER" -> MEMBER.in(language);
            case "GUARDIAN" -> GUARDIAN.in(language);
            case "TEAM" -> TEAM.in(language);
            case "MANAGER" -> MANAGER.in(language);
            default -> userType == null ? "" : userType;
        };
    }

    private final String german;
    private final String english;

    DocumentWord(String german, String english) {
        this.german = german;
        this.english = english;
    }

    /**
     * The word in a station's language.
     *
     * <p>Anything that is not English is German, which is the same way round as everywhere else a
     * language is chosen here: English is asked for by name and German is what a station gets when it
     * has said nothing.
     *
     * @param language {@code de} or {@code en}, as {@code StationFormat.languageOf} answers
     */
    public String in(String language) {
        return "en".equals(language) ? english : german;
    }
}

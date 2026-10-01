/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A refusal that has been given a code, with the sentence a reader is shown for it.
 *
 * <p>A reader who reports a failure can say what they were doing and what the screen said, and
 * neither of those finds the line that refused them. The code travels in the error body as
 * {@code code} and is short enough to be read out over a telephone, so a report arrives naming one
 * line rather than a kind of refusal thrown from eleven.
 *
 * <p>The code is one or two letters for the area, a hyphen and a number: {@code F-021}. The
 * prefixes are the registry in {@link Area}, which is the one place a feature claims one, so two
 * features cannot both be {@code F}. Every area keeps its refusals in an enum of its own in this
 * package, such as {@link InventoryRefusal}, and the number is that constant's own within the area
 * and belongs to nothing else, ever: see {@link RetiredRefusals} for what happens to the number of
 * a constant that is deleted.
 *
 * <p>One constant stands for one throw site. That is what makes the code worth quoting. The
 * exception is a pair of lines that deliberately answer alike, a lookup that missed and the
 * ownership check right behind it: handing those two codes would tell a caller that the thing
 * exists somewhere else, which is the one thing the shared answer is there to withhold. Those
 * share a constant and say so in their own words.
 *
 * <p>The code and the wording live together on purpose. A code kept in one place and a sentence
 * written at the throw site drift apart within a release; at the constant, changing one means
 * seeing the other. Where several refusals say the same thing, they name the same
 * {@code Sentences} constant rather than repeating it, so the prose has a single home.
 *
 * <p>The interface is sealed so that {@link #all()} knows every area's enum: an area enum that is
 * not permitted here does not compile.
 */
public sealed interface Refusal
        permits AdminRefusal,
                AttendanceRefusal,
                BeaconRefusal,
                BoardRefusal,
                BodyRefusal,
                ChecklistRefusal,
                ClusterRefusal,
                CommentRefusal,
                DiscoveryRefusal,
                DocumentRefusal,
                EquipmentRefusal,
                EventRefusal,
                FederationRefusal,
                FeedRefusal,
                FormRefusal,
                GeneralRefusal,
                InsightsRefusal,
                InstallationRefusal,
                InventoryRefusal,
                KnowledgeBaseRefusal,
                LegalRefusal,
                LostAndFoundRefusal,
                MailImportRefusal,
                MailRefusal,
                MapRefusal,
                MediaLibraryRefusal,
                MemberRefusal,
                NewsRefusal,
                PageRefusal,
                PasskeyRefusal,
                ProcedureRefusal,
                QuizRefusal,
                StationRefusal,
                StorageRefusal,
                SystemRefusal,
                TestProtocolRefusal,
                TrafficRefusal,
                TwoFactorRefusal,
                WaitingListRefusal {

    /**
     * Every refusal of every area.
     *
     * @return the constants of each area's enum, area by area
     */
    static List<Refusal> all() {
        return Arrays.stream(Refusal.class.getPermittedSubclasses())
                .flatMap(area -> Arrays.stream(area.getEnumConstants()))
                .map(Refusal.class::cast)
                .toList();
    }

    /**
     * What this refusal is made of: its area, its number, its status and its sentence.
     *
     * @return the definition the constant was declared with
     */
    Definition definition();

    /**
     * The constant's name, which a screen that has to tell one refusal from its neighbours matches on.
     *
     * @return the enum constant's name
     */
    String name();

    /**
     * The area of the product this refusal belongs to, which is what its prefix stands for.
     *
     * @return the area
     */
    default Area area() {
        return definition().area();
    }

    /**
     * This refusal's number within its area.
     *
     * @return the number, unique among the refusals of the same area
     */
    default int number() {
        return definition().number();
    }

    /**
     * The code a reader quotes, which names exactly one line.
     *
     * @return the area's prefix, a hyphen and three digits
     */
    default String code() {
        return definition().code();
    }

    /**
     * The status this refusal answers with, chosen so the reader is told truthfully whose problem
     * it is: what they sent is a {@code 400}, what they may not do is a {@code 403}, what is not
     * there is a {@code 404}, a collision is a {@code 409}, and only a fault is a {@code 500}.
     *
     * @return the HTTP status
     */
    default HttpStatus status() {
        return definition().status();
    }

    /**
     * The sentence the reader is shown.
     *
     * @return one sentence saying what was refused and what became of their data
     */
    default String message() {
        return definition().message();
    }

    /**
     * The exception to throw for this refusal.
     *
     * @return an exception carrying this refusal's status, sentence and code
     */
    default RefusalResponse raise() {
        return new RefusalResponse(this, message());
    }

    /**
     * The exception to throw for this refusal, naming the particular thing it was about.
     *
     * <p>The detail is the caller's own words back at them, a field name they sent or a value they
     * chose, never anything of Ember's.
     *
     * @param detail what the refusal was about, in the reader's terms, or {@code null} when there is
     *               nothing to name
     * @return an exception carrying this refusal's status and code, and its sentence with the
     *         detail named
     */
    default RefusalResponse raise(@Nullable String detail) {
        if (detail == null) return raise();
        return new RefusalResponse(this, message() + ": " + detail);
    }

    /**
     * What one refusal constant is declared with.
     *
     * @param area    the area the refusal belongs to
     * @param number  its number within the area
     * @param status  the status it answers with
     * @param message the sentence the reader is shown
     */
    record Definition(Area area, int number, HttpStatus status, String message) {
        /**
         * The code a reader quotes.
         *
         * @return the area's prefix, a hyphen and three digits
         */
        public String code() {
            return "%s-%03d".formatted(area.prefix(), number);
        }
    }

    /**
     * The registry of code prefixes, one per area of the product.
     *
     * <p>A prefix is claimed here and nowhere else, so two features cannot both be {@code F} and
     * nobody has to read every area's enum to find out what is free. An area with no refusals yet is
     * still listed: reserving the prefix is the point, because the alternative is two features
     * picking the same one months apart.
     *
     * <p>A prefix is one letter or two. The alphabet ran out long before the features did, and the
     * areas that had a letter when it did keep it, because a code already written down in a report
     * outlives the table that produced it. Everything added since takes two. The hyphen is what
     * makes the two widths safe to mix: a code is always the prefix, a hyphen and three digits, so
     * {@code F-001} and {@code FD-001} cannot be read for one another and no prefix can be
     * mistaken for the start of another.
     */
    public enum Area {
        /** Instance administration and the peers an instance knows of. */
        ADMIN("A", "instance administration and peers"),
        /** Attendance: who was there, and the sheets it is recorded on. */
        ATTENDANCE("AT", "attendance"),
        /** The request body, before any route has looked at it. */
        BODY("B", "the request body"),
        /** The beacon an installation reports to, and what it takes in. */
        BEACON("BC", "the beacon"),
        /** Boards, their tickets and what hangs off one. */
        BOARDS("BO", "boards and tickets"),
        /** Checklists and the runs of one. */
        CHECKLISTS("CL", "checklists"),
        /** Comments and notes, wherever they are left. */
        COMMENTS("CM", "comments and notes"),
        /** Clusters of stations and what they share. */
        CLUSTERS("CU", "clusters"),
        /** The documents a station keeps for its members. */
        DOCUMENTS("D", "documents"),
        /** Finding instances and the stations on them. */
        DISCOVERY("DC", "discovery"),
        /** Appointments and the registrations for them. */
        EVENTS("E", "appointments and registrations"),
        /** Equipment and what a station still needs. */
        EQUIPMENT("EQ", "equipment"),
        /** Forms, their answers and the links they are answered through. */
        FORMS("F", "forms and answers"),
        /** The feed a member reads, and the tokens it is read by. */
        FEED("FD", "the feed"),
        /** Anything with no area of its own: faults, the store, and the shared loaders. */
        GENERAL("G", "failures that belong to no one feature"),
        /** Inventory: items, containers, movements and checks. */
        INVENTORY("I", "inventory"),
        /** The figures a station is shown about itself. */
        INSIGHTS("IS", "insights"),
        /** The knowledge base. */
        KNOWLEDGE_BASE("K", "the knowledge base"),
        /** The media library: files, folders, tags and uploads. */
        MEDIA_LIBRARY("L", "the media library"),
        /** Things handed in that somebody lost. */
        LOST_AND_FOUND("LF", "lost and found"),
        /** Consent, and the terms somebody has agreed to. */
        LEGAL("LG", "consent and terms"),
        /** Members, accounts, avatars and profile questions. */
        MEMBERS("M", "members and accounts"),
        /** Reading members in from somewhere else. */
        MAIL_IMPORT("MI", "mail import"),
        /** Sending mail, and what a provider reports back about it. */
        MAIL("ML", "mail"),
        /** Maps and what is drawn on one. */
        MAPS("MP", "maps"),
        /** Setting an instance up, before it has any station. */
        INSTALLATION("N", "installation"),
        /** News, whether a station's own or the instance's. */
        NEWS("NW", "news"),
        /** Pages, their public addresses and their share links. */
        PAGES("P", "pages"),
        /** Passkeys, and registering or using one. */
        PASSKEYS("PK", "passkeys"),
        /** Catalogs, tests and the papers sat on them. */
        QUIZZES("Q", "quizzes"),
        /** Procedures and who they were handed to. */
        PROCEDURES("R", "procedures"),
        /** Stations and the applications to join one. */
        STATIONS("S", "stations"),
        /** Where an installation keeps its files, and reaching it. */
        STORAGE("ST", "storage"),
        /** The instance itself: its settings, its status and what it reports. */
        SYSTEM("SY", "the instance itself"),
        /** Test protocols, their sections and their points. */
        TEST_PROTOCOLS("T", "test protocols"),
        /** Second factors, and proving one. */
        TWO_FACTOR("TF", "two-factor"),
        /** What an instance counts about the requests it answers. */
        TRAFFIC("TR", "traffic"),
        /** Waiting lists, their entries and their invites. */
        WAITING_LISTS("W", "waiting lists"),
        /** Federation between instances. */
        FEDERATION("X", "federation");

        private final String prefix;
        private final String covers;

        Area(String prefix, String covers) {
            this.prefix = prefix;
            this.covers = covers;
        }

        /**
         * The letters this area's codes open with.
         *
         * @return one or two upper-case letters
         */
        public String prefix() {
            return prefix;
        }

        /**
         * What the prefix stands for, in the words somebody looking up a code would use.
         *
         * @return the area in a few words
         */
        public String covers() {
            return covers;
        }
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

/**
 * Generating the document a step of a procedure asks to sign and asking for its signatures, as the
 * documents and signing features do it, so the procedures feature does not need to know how.
 */
public interface DocumentStepSigning {
    /**
     * Whether a member may have the documents of steps generated and asked for on their behalf: a
     * current member of the station who generates documents for members.
     *
     * @param stationId the procedure's station
     * @param memberId  the member
     * @return whether documents may be asked for in their name
     */
    boolean mayAsk(int stationId, int memberId);

    /**
     * Generates a document for the member a procedure is about and files it with them, refused where
     * they are no current member of the station.
     *
     * @param stationId  the procedure's station
     * @param templateId the document template the step names
     * @param memberId   the member the procedure is about
     * @param askedBy    on whose behalf it is generated, as {@link #mayAsk} allows
     * @return the generation log entry of the document
     * @throws dev.chojo.ember.api.refusal.RefusalResponse where it cannot be generated
     */
    int generate(int stationId, int templateId, int memberId, int askedBy);

    /**
     * Asks the member a step's document is about, or their guardians, to sign it.
     *
     * @param generationId the generation log entry of the document
     * @param askedBy      on whose behalf it is asked for, as {@link #mayAsk} allows
     * @throws dev.chojo.ember.api.refusal.RefusalResponse where it cannot be asked for
     */
    void request(int generationId, int askedBy);

    /**
     * Withdraws the signatures a step's document still waits for. What was signed stays.
     *
     * @param generationId the generation log entry of the document
     * @param by           who withdraws
     */
    void withdraw(int generationId, int by);

    /**
     * Where the document a step made stands with its signatures.
     *
     * @param itemId       the step
     * @param generationId the generation log entry of its document
     * @return its state
     */
    StepDocumentState stateOf(int itemId, int generationId);
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;

/**
 * Who reaches a station's document store: the station itself, or the association it belongs to.
 *
 * <p>The store and its rules are the same through either door. What differs is only the words a
 * refusal is given in, since each door keeps the codes its readers already know.
 *
 * @see DocumentIntake
 */
public enum DocumentDoor {
    /** The station's own screens. */
    STATION(
            DocumentRefusal.DOCUMENTS_SWITCHED_OFF,
            new DocumentIntake.Refusals(
                    DocumentRefusal.DOCUMENT_UPLOAD_MISSING_FILE,
                    DocumentRefusal.DOCUMENT_UPLOAD_TOO_LARGE,
                    DocumentRefusal.DOCUMENT_UPLOAD_UNREADABLE,
                    DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM,
                    DocumentRefusal.DOCUMENT_UPLOAD_NOT_WHAT_IT_IS_CALLED)),

    /** The association's member screens. */
    ASSOCIATION(
            ClusterRefusal.CLUSTER_MANAGED_STATION_KEEPS_NO_DOCUMENTS,
            new DocumentIntake.Refusals(
                    ClusterRefusal.CLUSTER_MEMBER_DOCUMENT_MISSING_FILE,
                    ClusterRefusal.CLUSTER_MEMBER_DOCUMENT_TOO_LARGE,
                    ClusterRefusal.CLUSTER_MEMBER_DOCUMENT_UNREADABLE,
                    DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM,
                    DocumentRefusal.DOCUMENT_UPLOAD_NOT_WHAT_IT_IS_CALLED));

    private final Refusal switchedOff;
    private final DocumentIntake.Refusals intake;

    DocumentDoor(Refusal switchedOff, DocumentIntake.Refusals intake) {
        this.switchedOff = switchedOff;
        this.intake = intake;
    }

    /** @return what to refuse with where the station keeps no documents */
    public Refusal switchedOff() {
        return switchedOff;
    }

    /** @return what to refuse a file with that may not be taken in */
    public DocumentIntake.Refusals intake() {
        return intake;
    }
}

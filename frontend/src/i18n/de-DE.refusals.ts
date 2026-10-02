/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import admin from './refusals/admin'
import federation from './refusals/federation'
import beacon from './refusals/beacon'
import equipment from './refusals/equipment'
import lostAndFound from './refusals/lostAndFound'
import insights from './refusals/insights'
import attendance from './refusals/attendance'
import body from './refusals/body'
import news from './refusals/news'
import boards from './refusals/boards'
import documents from './refusals/documents'
import pages from './refusals/pages'
import procedures from './refusals/procedures'
import checklists from './refusals/checklists'
import mediaLibrary from './refusals/mediaLibrary'
import mailImport from './refusals/mailImport'
import inventory from './refusals/inventory'
import events from './refusals/events'
import comments from './refusals/comments'
import forms from './refusals/forms'
import waitingLists from './refusals/waitingLists'
import general from './refusals/general'
import clusters from './refusals/clusters'
import knowledgeBase from './refusals/knowledgeBase'
import members from './refusals/members'
import installation from './refusals/installation'
import system from './refusals/system'
import storage from './refusals/storage'
import traffic from './refusals/traffic'
import maps from './refusals/maps'
import mail from './refusals/mail'
import legal from './refusals/legal'
import feed from './refusals/feed'
import passkeys from './refusals/passkeys'
import twoFactor from './refusals/twoFactor'
import quizzes from './refusals/quizzes'
import stations from './refusals/stations'
import discovery from './refusals/discovery'
import testProtocols from './refusals/testProtocols'

/**
 * What each refusal code says in German, keyed by the code the backend sends.
 *
 * <p>The backend already writes a sentence for every refusal and sends it along, so a reader is
 * never left with nothing. This is that sentence in German: `describeFailure` looks up
 * `refusal.<code>` and shows the server's own English only where no key is written here. That is
 * what makes the files safe to fill in one code at a time.
 *
 * <p>Each area keeps its codes in a file of its own under `refusals/`, named after the area its
 * prefix stands for, and this object merges them. A new area adds its file there and its import here.
 *
 * <p>The keys are quoted because a code carries a hyphen. It is an ordinary character to the
 * message resolver, which treats only a dot and a bracket as structure, so `refusal.F-001` reaches
 * the object's `'F-001'` as one segment. Written unquoted it is not valid as an object key at all.
 *
 * <p>Several codes say the same thing: one lookup that missed and the ownership check behind it,
 * or the same object refused from eleven routes. Those name a constant rather than repeating the
 * sentence, so the wording has one home exactly as it does in the registry it mirrors. A sentence
 * one area says lives in that area's file; one that several areas say lives in `refusals/shared.ts`.
 *
 * <p>No sentence ends in a full stop, which is how the registry writes them. A screen that puts one
 * of these beside a sentence no one has translated yet would otherwise show two conventions at once.
 */
export default {
    ...admin,
    ...federation,
    ...beacon,
    ...equipment,
    ...lostAndFound,
    ...insights,
    ...attendance,
    ...body,
    ...news,
    ...boards,
    ...documents,
    ...pages,
    ...procedures,
    ...checklists,
    ...mediaLibrary,
    ...mailImport,
    ...inventory,
    ...events,
    ...comments,
    ...forms,
    ...waitingLists,
    ...general,
    ...clusters,
    ...knowledgeBase,
    ...members,
    ...installation,
    ...system,
    ...storage,
    ...traffic,
    ...maps,
    ...mail,
    ...legal,
    ...feed,
    ...passkeys,
    ...twoFactor,
    ...quizzes,
    ...stations,
    ...discovery,
    ...testProtocols,
}

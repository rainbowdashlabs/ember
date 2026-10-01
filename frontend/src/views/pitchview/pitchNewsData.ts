/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CommentResponse, MemberGroup, UserTag} from '@/api/generated/schema'
import type {PitchNews, PitchNewsSettings} from './pitchTypes'
import {pitchIdentity} from './pitchMembers'

/**
 * The news a demonstration shows, handed to the application's own list item and editor panels.
 * The content is rendered as the application renders it, so the previews carry real prose.
 */
function published(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() - days)
    return date.toISOString()
}

export const NEWS_ITEMS: PitchNews[] = [
    {
        kind: 'local', id: 1, title: 'Zeltlager 2026 - Anmeldung offen', publicBlog: true,
        author: pitchIdentity('Clara Weiß', 'm-clara'),
        publishedAt: published(2), commentCount: 4,
        contentHtml: '<p>Vom 12. bis 14. Juli geht es an den Talsee. Anmeldung läuft über den Termin, '
            + 'Rückfragen gern in den Kommentaren.</p>',
    },
    {
        kind: 'local', id: 2, title: 'Neue Ausrüstung ist da', restricted: true,
        author: pitchIdentity('Ben Krüger', 'm-ben'),
        publishedAt: published(5), commentCount: 0,
        contentHtml: '<p>Die neuen Helme sind eingetroffen. Anprobe am Dienstag, bitte alte Helme mitbringen.</p>',
    },
    {
        kind: 'federated', id: 3, title: 'Kreisjugendtag: die Termine stehen', stationName: 'Talbach',
        stationUid: 'talbach', authorName: 'Mia Berger', publishedAt: published(8), commentCount: 2,
        contentHtml: '<p>Der Kreisjugendtag findet am 20. September statt. Meldungen bitte bis Ende Juli.</p>',
    },
]

const GROUPS: MemberGroup[] = [
    {id: 1, stationId: 'wache', name: 'Löschgruppe', color: null, position: 0, groupSetId: null, userTypes: []},
    {id: 2, stationId: 'wache', name: 'Anwärter', color: null, position: 1, groupSetId: null, userTypes: []},
]

const TAGS: UserTag[] = [
    {id: 1, stationId: 'wache', name: 'Atemschutz', color: null, visible: true, position: 0},
    {id: 2, stationId: 'wache', name: 'Fahrdienst', color: null, visible: true, position: 1},
]

/** What the editor panels of a post show: who may read it, whether it is public, who else gets it. */
export const NEWS_SETTINGS: PitchNewsSettings = {
    groups: GROUPS,
    tags: TAGS,
    members: [],
    selectedUserTypes: [],
    selectedGroupIds: [1],
    selectedTagIds: [],
    selectedMemberIds: [],
    publicBlog: true,
    shared: true,
    scope: 'ALL_PARTNERS',
    partnerIds: [],
    visibilityRole: 'MEMBER',
    partners: [],
    canFederate: true,
}

/** The questions under the post, drawn by the application's own comment thread. */
export const NEWS_COMMENTS: CommentResponse[] = [
    {
        id: 1,
        author: pitchIdentity('Anna Müller', 'm-anna'),
        authorName: 'Anna Müller',
        content: 'Können Geschwister mitkommen, die noch nicht dabei sind?',
        deleted: false,
        createdAt: published(1),
    },
    {
        id: 2, parentId: 1,
        author: pitchIdentity('Clara Weiß', 'm-clara'),
        authorName: 'Clara Weiß',
        content: '@[Anna Müller] Ja, bitte bei der Anmeldung als Begleitperson eintragen.',
        deleted: false,
        createdAt: published(1),
    },
]

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Comment} from '@/api/comments'
import type {MemberGroup, UserTag} from '@/api/types'
import type {PitchNews, PitchNewsSettings} from './pitchTypes'

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
        kind: 'local', id: 1, title: 'Zeltlager 2026 — Anmeldung offen', publicBlog: true,
        author: {stationUid: 'wache', memberUid: 'm-clara', name: 'Clara Weiß'},
        publishedAt: published(2), commentCount: 4,
        contentHtml: '<p>Vom 12. bis 14. Juli geht es an den Talsee. Anmeldung läuft über den Termin, '
            + 'Rückfragen gern in den Kommentaren.</p>',
    },
    {
        kind: 'local', id: 2, title: 'Neue Ausrüstung ist da', restricted: true,
        author: {stationUid: 'wache', memberUid: 'm-ben', name: 'Ben Krüger'},
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
    {id: 1, stationId: 'wache', name: 'Löschgruppe', position: 0},
    {id: 2, stationId: 'wache', name: 'Anwärter', position: 1},
]

const TAGS: UserTag[] = [
    {id: 1, stationId: 'wache', name: 'Atemschutz'},
    {id: 2, stationId: 'wache', name: 'Fahrdienst'},
]

/** What the editor panels of a post show: who may read it, whether it is public, who else gets it. */
export const NEWS_SETTINGS: PitchNewsSettings = {
    groups: GROUPS,
    tags: TAGS,
    selectedUserTypes: [],
    selectedGroupIds: [1],
    selectedTagIds: [],
    publicBlog: true,
    shared: true,
    scope: 'ALL_PARTNERS',
    partnerIds: [],
    visibilityRole: 'MEMBER',
    partners: [],
    canFederate: true,
}

/** The questions under the post, drawn by the application's own comment thread. */
export const NEWS_COMMENTS: Comment[] = [
    {
        id: 1, parentId: null,
        author: {stationUid: 'wache', memberUid: 'm-anna', name: 'Anna Müller'},
        authorName: 'Anna Müller',
        content: 'Können Geschwister mitkommen, die noch nicht dabei sind?',
        createdAt: published(1),
    },
    {
        id: 2, parentId: 1,
        author: {stationUid: 'wache', memberUid: 'm-clara', name: 'Clara Weiß'},
        authorName: 'Clara Weiß',
        content: '@[Anna Müller] Ja, bitte bei der Anmeldung als Begleitperson eintragen.',
        createdAt: published(1),
    },
]

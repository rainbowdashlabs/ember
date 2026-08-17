/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CellConfig, CellContentTypeName, PageCell, PageRow, StationPage} from '@/api/pageManage'
import type {PitchPages} from './pitchTypes'

/**
 * The public page a demonstration shows. Rows and cells are handed to the renderer the website
 * itself uses, so the hero, the counters and the callout look exactly as a visitor sees them.
 */
function daysFromNow(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() + days)
    return date.toISOString()
}

function page(id: number, title: string, slug: string, published: boolean,
              parentId: number | null = null): StationPage {
    return {
        id, stationId: 1, parentId, title, slug, published, sortOrder: id,
        metaDescription: null, ogImageId: null, ogImageHash: null,
        createdBy: 1, createdAt: '', updatedAt: '', rows: [],
    }
}

export const PAGE_TREE = [
    {page: page(1, 'Startseite', 'start', true), depth: 0},
    {page: page(2, 'Über uns', 'ueber-uns', true), depth: 0},
    {page: page(3, 'Unsere Gruppen', 'gruppen', true, 2), depth: 1},
    {page: page(4, 'Mitmachen', 'mitmachen', true), depth: 0},
    {page: page(5, 'Fahrzeuge', 'fahrzeuge', false), depth: 0},
]

let cellId = 0

function cell(contentType: CellContentTypeName, content: string,
              config: CellConfig, widthPercent = 100): PageCell {
    cellId += 1
    return {id: cellId, rowId: 0, sortOrder: cellId, widthPercent, contentType, content, config}
}

function row(id: number, cells: PageCell[]): PageRow {
    return {id, pageId: 1, sortOrder: id, cells: cells.map(entry => ({...entry, rowId: id}))}
}

const ROWS: PageRow[] = [
    row(1, [
        cell('HERO_BANNER', '', {
            headline: 'Feuerwehr Musterstadt',
            subtitle: 'Seit 1897 für die Stadt da — und immer auf der Suche nach Verstärkung.',
            ctaText: 'Mitmachen', ctaUrl: '/p/mitmachen',
        }),
    ]),
    row(2, [
        cell('STATS_COUNTER', '', {
            items: [
                {value: '128', label: 'Mitglieder'},
                {value: '54', label: 'Einsätze', suffix: '/Jahr'},
                {value: '36', label: 'Dienstabende'},
                {value: '1897', label: 'gegründet'},
            ],
        }),
    ]),
    row(3, [
        cell('MARKDOWN',
            '## Wer wir sind\n\nWir sind eine freiwillige Wehr mit vier Gruppen, einer Jugendabteilung '
            + 'und einem Gerätehaus mitten im Ort. Wer mitmachen will, kommt einfach an einem '
            + 'Dienstabend vorbei.', {}, 60),
        cell('ADDRESS_CARD', '', {
            label: 'Gerätehaus', addressLine: 'Ringstraße 4', postalCode: '12345',
            city: 'Musterstadt', country: 'Deutschland',
        }, 40),
    ]),
    row(4, [
        cell('CALLOUT', 'Der nächste Schnupperabend ist am ersten Dienstag im Monat um 18:00 Uhr. '
            + 'Anmeldung ist nicht nötig.', {variant: 'INFO', title: 'Einfach vorbeikommen'}, 55),
        cell('COUNTDOWN', '', {
            targetDate: daysFromNow(24), label: 'Tag der offenen Tür', sublabel: 'Wir freuen uns auf euch',
        }, 45),
    ]),
]

export const PAGES: PitchPages = {tree: PAGE_TREE, rows: ROWS, landingPageId: 1}

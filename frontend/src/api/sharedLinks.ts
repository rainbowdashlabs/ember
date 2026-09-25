/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {StationPage} from './pageManage'

/**
 * The station behind a link somebody was sent, for the wrapper drawn around what the link opens.
 *
 * <p>It carries no station address of its own on purpose: the reader holds a link and nothing else,
 * and the station it belongs to is the answer rather than part of the question.
 */
export interface SharedBrand {
    stationUid: string
    publicSlug: string | null
    name: string
    hasLogo: boolean
    defaultTheme: string | null
    defaultFeel: string | null
    customThemeColors: string | null
    /** The clock every date on the page behind the link is written on. */
    timezone: string
}

export interface SharedPage {
    station: SharedBrand
    page: StationPage
    /** The path the page's slugs spell, for sending a reader on once it is public. */
    path: string
    /** Whether that path answers today, which needs the page public and the station's pages open. */
    ownAddressLive: boolean
}

export async function getSharedPage(token: string): Promise<SharedPage> {
    const res = await client.get<SharedPage>(`/public/shared/${token}`)
    return res.data
}

/**
 * The branding alone. The theme is chosen while the server renders, before the page itself has been
 * fetched, so it cannot wait for the answer above.
 */
export async function getSharedPageBrand(token: string): Promise<SharedBrand> {
    const res = await client.get<SharedBrand>(`/public/shared/${token}/brand`)
    return res.data
}

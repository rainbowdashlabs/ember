/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {stationLogoUrl} from '@/api/media'

/**
 * What a picture block holds in place of a media hash to show the station's logo, which is read where
 * the block is drawn, so a new logo reaches every block that shows it.
 */
export const STATION_LOGO = 'logo'

/** The size the logo is asked for in a block. */
const LOGO_SIZE = 512

/**
 * Where a picture block's picture is read from.
 *
 * @param stationUid the station the block belongs to
 * @param content    the block's content: a media hash, or {@link STATION_LOGO}
 * @param fileUrl    the address of a media file by its hash, as the surface reads files
 */
export function blockImageUrl(stationUid: string, content: string, fileUrl: (contentHash: string) => string): string {
    if (!content) return ''
    return content === STATION_LOGO ? stationLogoUrl(stationUid, LOGO_SIZE) : fileUrl(content)
}

/** The media hash of a picture block, nothing for the logo, which has no variants to choose from. */
export function blockImageHash(content: string): string | undefined {
    return content && content !== STATION_LOGO ? content : undefined
}

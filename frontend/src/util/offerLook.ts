/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {partnerPictureUrl} from '@/api/inventoryPictures'
import {glyphFor, type Glyph} from '@/util/glyph'

/** How a partner's offered gear is drawn in a list: its photo where it has one, and its icon. */
export interface OfferLook {
    pictureSrc: string | null
    glyph: Glyph
}

/**
 * The photo and the icon an offer is shown with, both resolved by the partner: the kind's where the
 * kind has them, otherwise the inventory's.
 *
 * @param offer what the partner offers
 * @param size  the side of the photo wanted
 */
/** What a partner's offer is drawn from: its station, its photo, and its icon where it has no photo. */
export interface OfferLookSource {
    stationId: string
    pictureKey: string | null
    icon: string | null
    color: string | null
    homogeneous: boolean
}

export function offerLook(offer: OfferLookSource, size = 64): OfferLook {
    return {
        pictureSrc: offer.pictureKey ? partnerPictureUrl(offer.stationId, offer.pictureKey, size) : null,
        glyph: glyphFor({icon: offer.icon, color: offer.color, homogeneous: offer.homogeneous}),
    }
}

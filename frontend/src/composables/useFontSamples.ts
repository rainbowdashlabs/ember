/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'
import type {FontSampleAddress} from '@/api/documentFonts'

const FONT_SAMPLES: InjectionKey<FontSampleAddress> = Symbol('fontSamples')

/**
 * Hands the font pickers below where the pictures of sample text are drawn: the owner's font source,
 * since a station, an association and the instance each see samples only of what they reach.
 */
export function provideFontSamples(address: FontSampleAddress): void {
    provide(FONT_SAMPLES, address)
}

/** Where the samples are drawn, or null where no screen above says so and the pickers show names only. */
export function useFontSamples(): FontSampleAddress | null {
    return inject(FONT_SAMPLES, null)
}

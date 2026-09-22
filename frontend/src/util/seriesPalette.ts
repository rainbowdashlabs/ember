/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The colours that tell the series of a chart apart, eight at most, in a fixed order.
 *
 * <p>Checked for colour-blind separation and lightness against the app's own surfaces, the light
 * set against the light background and the dark set against the dark one. The dark set is the same
 * hues stepped for the dark surface, not a second palette. On the light background several of these
 * sit below 3:1, so a chart using them labels its values or offers a table.
 *
 * <p>A ninth series never gets a colour of its own: a chart with more series than this shows a
 * table instead.
 */
const LIGHT = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7', '#e34948']
const DARK = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767']

/** The grey of a series that stands for "no value", which carries no identity of its own. */
const NEUTRAL_LIGHT = '#8a8a8a'
const NEUTRAL_DARK = '#9a9a9a'

/** How many series can be told apart by colour. */
export const SERIES_LIMIT = LIGHT.length

/**
 * The colour of the series in a given slot. Slots past the limit have none; callers check the
 * count first.
 *
 * @param slot the series' place in the fixed order, from zero
 * @param dark whether the page is painted dark
 */
export function seriesColor(slot: number, dark: boolean): string | undefined {
    return (dark ? DARK : LIGHT)[slot]
}

/** The grey for a series that stands for "no value". */
export function neutralSeriesColor(dark: boolean): string {
    return dark ? NEUTRAL_DARK : NEUTRAL_LIGHT
}

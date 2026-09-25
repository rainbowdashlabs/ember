/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The plot insets and legend placement every cartesian chart shares.
 *
 * Two collisions kept recurring because each chart invented its own numbers: a legend left at its
 * default position runs through the y-axis name, which ECharts draws in the same top-left corner,
 * and rotated category labels or a zoom slider run out of the bottom of the plot. Both are decided
 * here once, from what the chart actually carries, rather than per call site.
 */

export interface CartesianLayout {
    /** The chart draws a legend. It is placed below the plot, clear of the y-axis name. */
    legend?: boolean
    /** The y axis carries a name, which needs headroom above the plot. */
    axisName?: boolean
    /** The chart carries a heading, which needs the same headroom. */
    title?: boolean
    /** The category labels are rotated and reach further down than upright ones. */
    rotatedLabels?: boolean
    /** A zoom slider sits at the bottom of the chart. */
    zoom?: boolean
    /** Widen the left inset when the value labels are long (byte sizes, thousands separators). */
    left?: number
}

const BASE_BOTTOM = 30
const LEGEND_HEIGHT = 28
const ROTATED_LABEL_HEIGHT = 20
const ZOOM_SLIDER_HEIGHT = 40

/**
 * Insets for a chart whose legend sits at the bottom.
 *
 * @param layout what the chart carries
 * @returns the ECharts `grid` object
 */
export function cartesianGrid(layout: CartesianLayout = {}) {
    let bottom = BASE_BOTTOM
    if (layout.zoom) bottom += ZOOM_SLIDER_HEIGHT
    if (layout.rotatedLabels) bottom += ROTATED_LABEL_HEIGHT
    if (layout.legend) bottom += LEGEND_HEIGHT
    return {left: layout.left ?? 60, right: 20, top: layout.axisName || layout.title ? 40 : 24, bottom}
}

/**
 * A legend below the plot, which is the only place it never meets the y-axis name.
 *
 * @param color the text colour for the current theme
 * @param data the series names, when the chart names them explicitly
 * @returns the ECharts `legend` object
 */
export function bottomLegend(color: string, data?: string[]) {
    return {bottom: 0, left: 'center', textStyle: {color}, ...(data ? {data} : {})}
}

/**
 * A chart heading pinned to the very top, clear of whatever the plot draws below it.
 *
 * Ring charts need this most: left where ECharts puts it, the heading sits at the same height as
 * the outer labels reaching up from the top slice, and the two run through each other.
 *
 * @param text the heading
 * @param color the text colour for the current theme
 * @returns the ECharts `title` object
 */
export function chartTitle(text: string, color: string) {
    return {text, left: 'center', top: 0, textStyle: {fontSize: 14, color}}
}

/**
 * The colour axis labels, legends and headings are written in.
 *
 * <p>A chart draws its own text rather than inheriting the page's, so the theme has to be handed to
 * it. Every chart had been working this out for itself from the same two greys, which is three
 * copies of one decision and a fourth waiting to drift.
 *
 * @param dark whether the dark theme is on
 */
export function chartTextColor(dark: boolean): string {
    return dark ? '#ccc' : '#333'
}

/** Where a zoom slider sits so it clears the legend beneath it. */
export const ZOOM_SLIDER_BOTTOM = LEGEND_HEIGHT + 6

/**
 * Where a ring chart sits so its title and its labels stay clear of each other.
 *
 * <p>Above the middle rather than below it. The heading above needs less room than the leader lines
 * and the legend below, so the ring sits where the space left over is where the space is wanted.
 */
export const DONUT_CENTER = ['50%', '46%']

/**
 * The inner and outer radius every ring chart uses.
 *
 * <p>Held back from the edge to leave room for what the ring draws outside itself. A slice is named
 * on the end of a leader line pointing away from it, and the ones at the bottom point straight down
 * into the legend; drawn any larger, the names and the legend cross each other and neither can be
 * read. The two numbers keep their ratio, so the ring is the same shape as before.
 */
export const DONUT_RADIUS = ['34%', '58%']

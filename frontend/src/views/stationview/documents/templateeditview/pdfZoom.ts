/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The steps the page is zoomed through, as parts of the size at which the whole page fits. */
export const ZOOM_STEPS: readonly number[] = [1, 1.25, 1.5, 2, 3, 4]

/**
 * The next step in one direction, staying at the end where there is none further.
 *
 * @param zoom      the zoom now
 * @param direction 1 to zoom in, -1 to zoom out
 * @return the zoom after the step
 */
export function zoomStep(zoom: number, direction: 1 | -1): number {
    const index = ZOOM_STEPS.findIndex(step => step >= zoom)
    const current = index < 0 ? ZOOM_STEPS.length - 1 : index
    const next = Math.min(Math.max(current + direction, 0), ZOOM_STEPS.length - 1)
    return ZOOM_STEPS[next] ?? zoom
}

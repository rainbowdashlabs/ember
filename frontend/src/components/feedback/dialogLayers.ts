/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Which of two open dialogs is in front.
 *
 * Opening order decides, not the order the components were mounted in. The step-up challenge is
 * what makes that necessary: it is raised by the API layer from wherever the reader happened to be,
 * it is mounted once for the whole app, and its content therefore lands in the document before any
 * dialog a page opens later. With one fixed layer for every dialog it came up behind the one whose
 * action had asked for it, leaving the reader a code field they could neither see nor type into.
 *
 * The count falls back to the base as soon as nothing is open, so the numbers stay small and the
 * toasts keep their place above every dialog.
 */
const BASE_LAYER = 50

let openDialogs = 0
let topLayer = BASE_LAYER

/** The layer a dialog paints on while it is closed. */
export const baseDialogLayer = BASE_LAYER

/** Takes the layer in front of every dialog that is currently open. */
export function claimDialogLayer(): number {
    openDialogs++
    return ++topLayer
}

/** Gives back a layer taken by {@link claimDialogLayer}. */
export function releaseDialogLayer(): void {
    openDialogs--
    if (openDialogs <= 0) {
        openDialogs = 0
        topLayer = BASE_LAYER
    }
}

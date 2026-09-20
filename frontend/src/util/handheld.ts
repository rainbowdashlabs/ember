/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether this is a device held in the hand rather than sat in front of.
 *
 * <p>Having no mouse or trackpad anywhere is what decides it, alongside the Apple devices that have
 * the defect whatever is plugged into them. Asked once inside a press rather than watched, so it has
 * no lifecycle to attach to and stays a plain function.
 *
 * <p>What hangs on the answer is how a file reaches the reader. A handheld browser will not take bytes
 * the page hands it: it offers to save them and then does not, or navigates to them and draws an empty
 * page. Reading the document where it already is, or handing it to the system, works there; a download
 * link is what does not.
 */
export function isHandheld(): boolean {
    return isAppleTouchDevice() || !hasAnyFinePointer()
}

/**
 * Whether the device has a mouse or a trackpad at all, rather than whether it is being used now.
 *
 * <p>Asked of every pointer the device has and not only of its main one, which is what keeps a
 * convertible folded into a tablet, a laptop with a touchscreen and a kiosk on the saving path they
 * always had: each of them can save a file perfectly well, and each of them answers a question about
 * its primary pointer the way a phone does. A browser too old to understand the question is taken
 * for a desk, since that is what the behaviour was before any of this.
 */
function hasAnyFinePointer(): boolean {
    const query = window.matchMedia('(any-pointer: fine)')
    return query.media === 'not all' || query.matches
}

/** An iPhone or iPad, including an iPad with a trackpad attached and an iPad presenting itself as a Mac. */
function isAppleTouchDevice(): boolean {
    const agent = navigator.userAgent
    return /iPhone|iPad|iPod/.test(agent) || (agent.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

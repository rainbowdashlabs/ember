/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether this is a device held in the hand rather than sat in front of.
 *
 * <p>Having no mouse is what decides it, which is the same question {@code useFinePointer} asks for
 * whether a list can be dragged. This one is asked once inside a press rather than watched, so it has
 * no lifecycle to attach to and stays a plain function.
 *
 * <p>What hangs on the answer is how a file reaches the reader. A handheld browser will not take bytes
 * the page hands it: it offers to save them and then does not, or navigates to them and draws an empty
 * page. Reading the document where it already is, or handing it to the system, works there; a download
 * link is what does not.
 */
export function isHandheld(): boolean {
    return !window.matchMedia('(pointer: fine)').matches || isAppleTouchDevice()
}

/** An iPhone or iPad, including an iPad with a trackpad attached and an iPad presenting itself as a Mac. */
function isAppleTouchDevice(): boolean {
    const agent = navigator.userAgent
    return /iPhone|iPad|iPod/.test(agent) || (agent.includes('Macintosh') && navigator.maxTouchPoints > 1)
}

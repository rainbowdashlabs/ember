/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Cover} from '@/composables/useScreenCapture'

/** What a page marks its members' names with, wherever one is drawn. */
const NAME = '[data-testid="member-name"]'

/** What a profile answer is drawn in, which is where a birth date or an address ends up. */
const ANSWER = '[data-field]'

/** An address somebody can be written to at. */
const MAIL = 'a[href^="mailto:"]'

const PASSWORD = 'input[type="password"]'

/** How far the picture's proportions may differ from the page's before the two are not the same thing. */
const TOLERANCE = 0.02

/**
 * What one page pixel is worth in the picture, or null where the picture is not of this page alone.
 *
 * <p>A person sharing one tab hands over exactly the page, and every element on it can be found in
 * the picture by a single factor. A person sharing a whole window or a whole screen hands over the
 * browser's own furniture as well, and there is no way from here to tell how much of it: boxes drawn
 * by calculation would land beside what they were meant to cover, which is worse than not offering
 * to draw them. So the proportions are compared, and where they disagree nothing is offered.
 */
export function pageMapping(picture: {width: number; height: number}): number | null {
    if (typeof window === 'undefined') return null
    const pageWidth = window.innerWidth
    const pageHeight = window.innerHeight
    if (!pageWidth || !pageHeight || !picture.width || !picture.height) return null
    const pageRatio = pageWidth / pageHeight
    const pictureRatio = picture.width / picture.height
    if (Math.abs(pageRatio - pictureRatio) / pageRatio > TOLERANCE) return null
    return picture.width / pageWidth
}

/** Whether the ready-made covers can be offered at all for this picture. */
export function canCoverAutomatically(picture: {width: number; height: number}): boolean {
    return pageMapping(picture) !== null
}

function coversFor(picture: {width: number; height: number}, selector: string): Cover[] {
    const factor = pageMapping(picture)
    if (factor === null || typeof document === 'undefined') return []
    const found: Cover[] = []
    for (const element of Array.from(document.querySelectorAll(selector))) {
        const box = element.getBoundingClientRect()
        if (box.width <= 0 || box.height <= 0) continue
        if (box.bottom < 0 || box.top > window.innerHeight) continue
        found.push({
            x: Math.round(box.left * factor),
            y: Math.round(box.top * factor),
            width: Math.round(box.width * factor),
            height: Math.round(box.height * factor),
        })
    }
    return found
}

/**
 * The parts of the page that name people or answer questions about them.
 *
 * <p>Offered as one press because covering thirty names by hand is thirty chances to miss one. It is
 * not a promise that nothing personal remains: it covers what the page marks as a name, an answer or
 * an address, and a page that writes somebody's data some other way is still for the reader to see
 * and cover.
 */
export function coversForPersonalData(picture: {width: number; height: number}): Cover[] {
    return [NAME, ANSWER, MAIL].flatMap(selector => coversFor(picture, selector))
}

/**
 * Whatever is standing in a password field, covered before the picture is ever shown.
 *
 * <p>The one covering nobody is asked about, because there is no report in which the characters of a
 * password belong, and the field usually shows dots rather than letters only until somebody presses
 * the eye beside it.
 */
export function coversForPasswords(picture: {width: number; height: number}): Cover[] {
    return coversFor(picture, PASSWORD)
}

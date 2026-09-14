#!/usr/bin/env node
/**
 * Button row linter for the Ember frontend.
 *
 * Several buttons on one narrow line run out of room and each breaks its own label, leaving a row of
 * buttons at two different heights. `ButtonRow` settles that once: a grid below the breakpoint, the
 * plain flex row above it. This finds the rows still written by hand.
 *
 * A row is reported when a flex container holds two or more buttons that carry a label and says
 * nothing about what happens at a narrow width. A button holding only an icon is a toolbar rather
 * than an action row and has no label to break, so it does not count towards the two.
 *
 * Warns while the sweep is under way. Pass --error to fail the build on what is left.
 */

import {readFileSync} from 'fs'
import {SRC, walk, extractTemplate, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Hand written button row'

const asError = process.argv.includes('--error')

const BUTTON = /<((?:Primary|Secondary|Error|Success|Info|Save|Delete|Edit|Confirm|Download|Upload|Link)Button)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/\1>)/g
const REPEATED = /\sv-for\b/
const ALTERNATIVE = /\sv-else\b|\sv-else-if\b/
const DIV = /<div\b([^>]*)>|<\/div>/g
const MANAGED = /<ButtonRow\b[\s\S]*?<\/ButtonRow>/g
const SCREEN_READER_ONLY = /<span[^>]*\bsr-only\b[\s\S]*?<\/span>/g
const CLASSES = /class="([^"]*)"/
const RESPONSIVE = /\b(?:sm|md|lg|xl):|flex-col|\bgrid\b/

/**
 * Whether the tag lays its children out in a row. The class has to be `flex` itself: `flex-1` sizes
 * a child inside somebody else's row and lays out nothing of its own.
 */
function laysOutARow(attributes) {
    const classes = CLASSES.exec(attributes)
    return classes !== null && classes[1].split(/\s+/).includes('flex')
}

/**
 * Whether the button says anything a reader could read. Its own tags do not count, so a button
 * holding nothing but an icon comes back false; a mustache does count, because that is where every
 * translated label sits. Words put there only for a screen reader do not count either: on the page
 * that button is an icon, and an icon has nothing to break onto a second line.
 */
function carriesLabel(inner) {
    if (inner === undefined) return false
    return inner.replace(SCREEN_READER_ONLY, '').replace(/<[^>]*>/g, '').trim() !== ''
}

/**
 * How many labelled buttons the given slice of template holds loose. Buttons already handed to a
 * `ButtonRow` are somebody else's problem, so the rows around one are not reported again. A button
 * standing as the alternative to another never shares the line with it, so it is not counted: one
 * of the two is all the page ever draws.
 */
function labelledButtonsIn(slice) {
    let count = 0
    for (const match of slice.replace(MANAGED, '').matchAll(BUTTON)) {
        if (REPEATED.test(match[2])) return 0
        if (ALTERNATIVE.test(match[2])) continue
        if (carriesLabel(match[3])) count++
    }
    return count
}

/**
 * What the row itself lays out, with every nested div left out. A button inside one of those is laid
 * out by that div and not by this row, so counting it here would blame a page shell for the buttons
 * scattered down its branches.
 */
function ownContentOf(template, tags, position) {
    const opening = tags[position]
    const kept = []
    let depth = 1
    let from = opening.index + opening[0].length

    for (const inner of tags.slice(position + 1)) {
        if (inner[1] !== undefined) {
            if (depth === 1) kept.push(template.slice(from, inner.index))
            depth++
            continue
        }
        depth--
        if (depth === 1) from = inner.index + inner[0].length
        if (depth === 0) {
            kept.push(template.slice(from, inner.index))
            return kept.join('')
        }
    }
    kept.push(template.slice(from))
    return kept.join('')
}

/**
 * Reports every hand written flex row of one template. A row is found by its opening tag and closed
 * by counting the divs that nest inside it, so the slice examined is exactly that container.
 */
function reportRowsIn(file, template, lineOf) {
    const tags = [...template.matchAll(DIV)]

    for (const [position, opening] of tags.entries()) {
        const attributes = opening[1]
        if (attributes === undefined) continue
        if (!laysOutARow(attributes) || RESPONSIVE.test(attributes)) continue

        const labelled = labelledButtonsIn(ownContentOf(template, tags, position))
        if (labelled < 2) continue

        const report = asError ? reporter.error : reporter.warn
        report(
            file,
            lineOf(opening.index),
            `${labelled} buttons share a hand written row. Use ButtonRow so they stack on a phone.`,
            CATEGORY,
        )
    }
}

for (const file of walk(SRC, '.vue')) {
    const content = readFileSync(file, 'utf8')
    const template = extractTemplate(content)
    if (template === '') continue
    const offset = content.slice(0, content.indexOf(template)).split('\n').length - 1
    const lineOf = index => offset + template.slice(0, index).split('\n').length
    reportRowsIn(file, template, lineOf)
}

reporter.print()
reporter.exit()

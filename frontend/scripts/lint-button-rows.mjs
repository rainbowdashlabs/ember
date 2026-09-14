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
 * than an action row and has no label to break, so it does not count towards the two. A row built by
 * repeating one button is left alone altogether: a bar of tabs or of filters is a collection rather
 * than a row of separate actions, and stacking it full width would be the wrong answer.
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
const DIV = /<div\b([^>]*)>|<\/div>/g
const MANAGED = /<ButtonRow\b[\s\S]*?<\/ButtonRow>/g
const FLEX = /class="[^"]*\bflex\b/
const RESPONSIVE = /\b(?:sm|md|lg|xl):|flex-col|\bgrid\b/

/**
 * Whether the button says anything a reader could read. Its own tags do not count, so a button
 * holding nothing but an icon comes back false; a mustache does count, because that is where every
 * translated label sits.
 */
function carriesLabel(inner) {
    if (inner === undefined) return false
    return inner.replace(/<[^>]*>/g, '').trim() !== ''
}

/**
 * How many labelled buttons the given slice of template holds loose. Buttons already handed to a
 * `ButtonRow` are somebody else's problem, so the rows around one are not reported again.
 */
function labelledButtonsIn(slice) {
    let count = 0
    for (const match of slice.replace(MANAGED, '').matchAll(BUTTON)) {
        if (REPEATED.test(match[2])) return 0
        if (carriesLabel(match[3])) count++
    }
    return count
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
        if (!FLEX.test(attributes) || RESPONSIVE.test(attributes)) continue

        let depth = 1
        let end = template.length
        for (const inner of tags.slice(position + 1)) {
            depth += inner[1] === undefined ? -1 : 1
            if (depth === 0) {
                end = inner.index
                break
            }
        }

        const labelled = labelledButtonsIn(template.slice(opening.index, end))
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

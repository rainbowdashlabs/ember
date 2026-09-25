#!/usr/bin/env node
/**
 * Finds rows, cards and names that open a page without being links.
 *
 * Pressing one of these opens a page, so each of them is a link. Written as a click handler on a
 * `div` it is not one: no middle click, no address to copy, nothing in the status bar, nothing in
 * the tab order, and nothing for a screen reader to announce or to list. None of that shows up to
 * somebody testing with a mouse, which is how it spread to most of the lists in the product.
 *
 * Three kinds of navigation have to be told apart, and only the first is a link:
 *
 * 1. Opening a page the reader chose. A row, a card, a name. Reported here.
 * 2. Going on after doing something. Saving and landing on what was saved, a wizard step. There is
 *    nothing to open in a tab, because the page does not exist until the act happens.
 * 3. Going back. A back button restores a place rather than addressing one.
 *
 * So what is reported is narrow on purpose: a handler whose whole body is a navigation, on an
 * element that is not a link and is not a button. A handler that saves first, or asks first, is the
 * second kind and is left alone.
 *
 * It fails the build. It warned while the lists were being converted one area at a time; every one
 * of them is a link now, so anything it finds from here is a new one, and a new one is cheaper to
 * refuse than to sweep up later.
 */

import {readFileSync} from 'fs'
import {SRC, createReporter, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Page link'

/** What to use instead, named in every report so nobody has to go looking. */
const REMEDY = 'a row that opens a page is a link: wrap it in RowLink (components/navigation/RowLink.vue)'

/**
 * Elements that are already a link or already a control.
 *
 * <p>A button that navigates is a different argument and not this one: it is often the third kind,
 * going back, and where it is not, turning it into a link is a design decision rather than a
 * mechanical one. An entry of a menu is a button too, whatever it is named.
 */
const NOT_REPORTED = /^(a|button|NuxtLink|RouterLink|router-link|RowLink|AppLink|SidebarLink|HelpCenterLink|IconButton|[A-Za-z]*Button|[A-Za-z]*MenuItem)$/

/**
 * A click handler whose whole body is a navigation.
 *
 * <p>Both spellings: the push written in the template, and a handler naming a function whose body
 * turns out to be nothing but a push.
 */
const INLINE_PUSH = /@click(?:\.\w+)*\s*=\s*"(?:\(\)\s*=>\s*)?(?:\$?router|navigateTo)[.(][^"]*"/
const HANDLER_NAME = /@click(?:\.\w+)*\s*=\s*"([A-Za-z_$][\w$]*)(?:\([^"]*\))?"/

/** The element a click handler sits on, read back from the line it is written on. */
function elementOf(lines, at) {
    for (let i = at; i >= 0 && i > at - 25; i--) {
        const opener = lines[i].match(/<([A-Za-z][\w.-]*)/)
        if (opener) return opener[1]
    }
    return ''
}

/**
 * Whether a named handler does nothing but navigate.
 *
 * <p>Read from the file's own script block rather than guessed at: a handler that awaits a save, or
 * opens a dialog, or checks something first, is the second kind of navigation and says so by having
 * more than one statement in it.
 */
function onlyNavigates(content, name) {
    const declaration = new RegExp(
        `function\\s+${name}\\s*\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\n\\}`,
        'm',
    )
    const found = content.match(declaration)
    if (!found) return false
    const body = found[1]
        .split('\n')
        .map(line => line.replace(/\/\/.*$/, '').trim())
        .filter(line => line.length > 0)
    if (body.length !== 1) return false
    return /^(?:return\s+)?(?:void\s+)?(?:router|navigateTo)[.(]/.test(body[0]);
}

/**
 * What one component has to answer for, as a line number and the element on it.
 *
 * <p>Separated from the walk so the rule itself can be put to a test: what it must catch and what
 * it must leave alone are the whole of its value, and neither is obvious from reading it.
 *
 * @param content the component's source
 * @return one entry per row that opens a page without being a link
 */
export function rowsThatShouldBeLinks(content) {
    const lines = content.split('\n')
    const found = []
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        const inline = INLINE_PUSH.test(line)
        const named = line.match(HANDLER_NAME)
        if (!inline && !named) continue
        if (!inline && !onlyNavigates(content, named[1])) continue

        const element = elementOf(lines, i)
        if (NOT_REPORTED.test(element)) continue

        found.push({line: i + 1, element})
    }
    return found
}

function run() {
    let reported = 0
    for (const file of walk(SRC, '.vue')) {
        for (const row of rowsThatShouldBeLinks(readFileSync(file, 'utf-8'))) {
            reported++
            reporter.error(rel(file), row.line, `<${row.element}> opens a page from a click handler. ${REMEDY}`, CATEGORY)
        }
    }

    console.log(`Checked every component for rows that open a page without being links: ${reported} found.`)
    reporter.print()
    process.exit(reporter.errors.length > 0 ? 1 : 0)
}

if (process.argv[1] && process.argv[1].endsWith('lint-page-links.mjs')) run()

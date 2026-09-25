#!/usr/bin/env node
/**
 * Finds pages whose address names one particular thing while their title names only the kind.
 *
 * An address with a parameter in it points at one catalogue, one ticket, one member. The title of
 * such a page should say which: `ViewContent` writes its title into the shared header, and that is
 * what the page header shows, what the browser tab reads, and what a bookmark and a history entry
 * keep. Titled after the kind, a dozen open tabs all read "Fragenkatalog" and none of them says
 * which catalogue, which is the complaint this rule comes from.
 *
 * What counts as naming the thing is simply that the title depends on something the page loaded. A
 * title that is nothing but a translated constant cannot, whatever the constant says. A title
 * falling back to that constant while the record is on its way is right and passes, because the
 * expression still reads the record.
 *
 * Only a required parameter counts. An optional one marks a page reached both with and without it,
 * where a constant title is often the honest one.
 *
 * A page writing its own document title through `useHead` is left alone. Those are the public
 * pages, where the heading over the content and the title of the tab are two different strings:
 * "Neuigkeiten" is the right heading on a station's blog, while the tab has to say whose blog it
 * is, and it does, from the head. What such a page puts there is governed by the social meta rule,
 * which reads the head rather than the template, so this one would be reading the wrong string.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {SRC, createReporter, extractTemplate, parseRoutes, rel} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Page title'

/** What to do instead, named in every report so nobody has to work it out twice. */
const REMEDY = 'name the thing the address points at, falling back to the constant while it loads'

/** A required parameter, which is what makes an address point at one thing rather than a kind. */
const NAMES_ONE_THING = /:[A-Za-z]\w*(?!\?)/

/**
 * The `title` handed to `ViewContent`, bound or written out, or nothing where the page hands none.
 *
 * <p>The opening tag is read as a whole because these are written over several lines as often as
 * not, and the attribute is as likely to be the fourth as the first.
 */
export function viewContentTitle(content) {
    const template = extractTemplate(content)
    const opening = template.match(/<ViewContent\b[^>]*>/)
    if (!opening) return null

    const bound = opening[0].match(/:title="([^"]*)"/)
    if (bound) return {expression: bound[1], written: false}

    const written = opening[0].match(/\stitle="([^"]*)"/)
    return written ? {expression: written[1], written: true} : null
}

/**
 * Whether a title can only ever read the same, which is what the rule is about.
 *
 * <p>A title written out rather than bound is a constant by construction, whatever it says. A bound
 * one is read by taking out the translation calls and the strings, since a constant stays one
 * however it is spelled: what a page loaded reaches the title as a name, so a name left over is
 * what says the title can change. What a template literal interpolates is kept, because that is
 * where the names are in the one title spelled as a sentence.
 */
export function isConstantTitle(title) {
    if (title.written) return true

    const interpolated = [...title.expression.matchAll(/\$\{([^}]*)\}/g)].map(match => match[1]).join(' ')
    const withoutTranslations = title.expression.replace(/\bt\(\s*(['"])(?:\\.|(?!\1).)*\1\s*\)/g, ' ')
    const withoutStrings = withoutTranslations.replace(/(['"`])(?:\\.|(?!\1).)*\1/g, ' ')
    return !/[A-Za-z_$]/.test(withoutStrings) && !/[A-Za-z_$]/.test(interpolated)
}

/** Whether the page names its own tab, which is the public pages and nothing else. */
export function writesItsOwnHead(content) {
    return /\buseHead\s*\(/.test(content)
}

/** Where a route's component lives, or nothing where the page names none. */
function componentFile(component) {
    if (!component || !component.startsWith('@/')) return null
    return join(SRC, component.slice(2))
}

function run() {
    let checked = 0
    for (const route of parseRoutes()) {
        if (!NAMES_ONE_THING.test(route.path)) continue

        const file = componentFile(route.component)
        if (!file) continue

        let content
        try {
            content = readFileSync(file, 'utf-8')
        } catch {
            continue
        }

        if (writesItsOwnHead(content)) continue

        const title = viewContentTitle(content)
        if (!title) continue

        checked++
        if (!isConstantTitle(title)) continue

        const shown = title.written ? `"${title.expression}"` : `:title="${title.expression}"`
        reporter.error(rel(file), 1, `${route.path} points at one thing, ${shown} names a kind. ${REMEDY}`, CATEGORY)
    }

    console.log(`Checked every page whose address names one thing for a title that says which: ${checked} read.`)
    reporter.print()
    process.exit(reporter.errors.length > 0 ? 1 : 0)
}

if (process.argv[1] && process.argv[1].endsWith('lint-context-titles.mjs')) run()

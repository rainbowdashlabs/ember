#!/usr/bin/env node
/**
 * Holds the two changelogs to the same releases.
 *
 * The product speaks German and the notes ship in both languages, so a release written on one side
 * only is read as a smaller release by whoever reads the other. The German file starts where it was
 * introduced and carries a tail of the English one, which is allowed; naming a version English does
 * not, reordering the shared ones, or carrying fewer entries under a shared heading is not.
 *
 * Stands down where the repository root is absent, which is the frontend image.
 */

import {existsSync, readFileSync} from 'fs'
import {join} from 'path'
import {createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Changelog languages'

const REPO_ROOT = new URL('../..', import.meta.url).pathname
const ENGLISH = 'CHANGELOG.md'
const GERMAN = 'CHANGELOG.de.md'

const HEADING = /^##\s+(v?\d+(?:\.\d+)*)\s*$/
const ENTRY = /^-\s/

/** Every version the file names, in the order it names them, with the entries under each. */
function releases(path) {
    const found = new Map()
    let current = null
    for (const line of readFileSync(path, 'utf-8').split('\n')) {
        const heading = HEADING.exec(line)
        if (heading) {
            current = heading[1]
            found.set(current, 0)
            continue
        }
        if (current && ENTRY.test(line)) found.set(current, found.get(current) + 1)
    }
    return found
}

const paths = [ENGLISH, GERMAN].map(name => join(REPO_ROOT, name))
if (!paths.every(existsSync)) {
    // Stop here rather than fall through: the reporter only ends the run on an error, and what
    // follows would read a file that is not there.
    reporter.warn('', 0, 'The changelogs are not present, which is expected inside the frontend image.', CATEGORY)
    reporter.print()
    process.exit(0)
}

const english = releases(paths[0])
const german = releases(paths[1])

for (const version of german.keys()) {
    if (!english.has(version)) {
        reporter.error(GERMAN, 0, `names ${version}, which ${ENGLISH} does not.`, CATEGORY)
    }
}

const shared = [...english.keys()].filter(version => german.has(version))
const inGerman = [...german.keys()].filter(version => english.has(version))
if (shared.join() !== inGerman.join()) {
    reporter.error(GERMAN, 0, `orders the shared versions differently: ${inGerman.join(', ')}`, CATEGORY)
}

if (shared[0] !== inGerman[0]) {
    reporter.error(
        GERMAN,
        0,
        `the newest version is ${shared[0]} in English and ${inGerman[0]} here. A release translated on `
        + 'one side only shows the other language.',
        CATEGORY,
    )
}

for (const version of shared) {
    const here = english.get(version)
    const there = german.get(version)
    if (here !== there) {
        reporter.error(GERMAN, 0, `${version} carries ${here} entries in ${ENGLISH} and ${there} here.`, CATEGORY)
    }
}

if (reporter.errors.length === 0) {
    console.log(`Both changelogs carry the same ${shared.length} release(s), entries included.`)
}
reporter.print()
reporter.exit()

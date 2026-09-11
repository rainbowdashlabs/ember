/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readdirSync, readFileSync} from 'node:fs'
import {dirname, join} from 'node:path'
import {fileURLToPath} from 'node:url'

/**
 * The end-to-end stories, divided into the runs CI makes of them.
 *
 * The suite is one stack and one database, so the whole of it used to be one job of twenty minutes.
 * Divided, the runs happen at once and a failure names an area before anybody opens the report.
 *
 * The division is written down rather than counted out, because a share of the files tells nobody
 * anything: "group three failed" is not a sentence. What it costs is that a new story has to be put
 * somewhere, which is what `check` is for. A story in no group would otherwise be a story that quietly
 * stops running, and that is worse than an unbalanced run.
 */

const here = dirname(fileURLToPath(import.meta.url))
const e2eDir = join(here, '..', 'e2e')

const groups = JSON.parse(readFileSync(join(e2eDir, 'groups.json'), 'utf8'))

/** Every story on disk, by the name it is known under: the file without its suffix. */
function storiesOnDisk() {
    return readdirSync(e2eDir)
        .filter(name => name.endsWith('.e2e.ts'))
        .map(name => name.slice(0, -'.e2e.ts'.length))
        .sort()
}

/**
 * Whether every story belongs to exactly one run, and every name in a run is a story.
 *
 * @returns the complaints, empty when the division covers the suite
 */
export function check() {
    const onDisk = new Set(storiesOnDisk())
    const seen = new Map()
    const problems = []

    for (const [group, stories] of Object.entries(groups)) {
        for (const story of stories) {
            if (!onDisk.has(story)) problems.push(`${group} names ${story}, which is not a story`)
            if (seen.has(story)) problems.push(`${story} is in both ${seen.get(story)} and ${group}`)
            seen.set(story, group)
        }
    }
    for (const story of onDisk) {
        if (!seen.has(story)) problems.push(`${story} is in no group, so nothing would run it`)
    }
    return problems
}

/**
 * The file patterns Playwright is given for one run.
 *
 * @param name the run
 * @returns the patterns, anchored so one story's name cannot match another's
 */
export function patternsOf(name) {
    const stories = groups[name]
    if (!stories) throw new Error(`No such group: ${name}. There are ${Object.keys(groups).join(', ')}`)
    return stories.map(story => `e2e/${story}.e2e.ts`)
}

export function names() {
    return Object.keys(groups)
}

const [, , command, argument] = process.argv
if (command === 'check') {
    const problems = check()
    problems.forEach(problem => console.error(problem))
    if (problems.length > 0) process.exit(1)
    console.log(`All ${storiesOnDisk().length} stories are in one of ${names().length} groups.`)
} else if (command === 'patterns') {
    console.log(patternsOf(argument).join(' '))
} else if (command === 'names') {
    console.log(JSON.stringify(names()))
}

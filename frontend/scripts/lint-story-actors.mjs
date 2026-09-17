#!/usr/bin/env node
/**
 * Actor linter for the end-to-end stories.
 *
 * Who a story acts as is settled once, at global setup, and written down by id. A story that works
 * it out for itself gets the first account matching a description, which is the same answer in every
 * worker: four of them converge on one person. Worse, the descriptions read the address, and stories
 * rewrite addresses, so a pool keyed on it hands out somebody else's part halfway through a run.
 *
 * This finds the two ways back into that. Asking the instance who exists, from a spec rather than
 * from the fixtures, and comparing an identity by address or by name rather than by id.
 *
 * The fixtures are exempt: casting is where the addresses are read, once, before anything has moved.
 *
 * Pass --error to fail the build rather than warn.
 */

import {readFileSync, readdirSync, statSync} from 'fs'
import {join} from 'path'
import {createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Story actor'

const asError = process.argv.includes('--error')
const E2E = 'e2e'

/** Asking who exists. The fixtures may; a story may not. */
const DISCOVERY = /\b(demoAccounts|accountWith|accountWithout|stationPeers|instanceAdmin)\s*\(/

/**
 * Comparing an identity by something a story can rewrite.
 *
 * <p>Only where an address or a name is on one side of the comparison, so that comparing a label or
 * a status is left alone.
 */
const MUTABLE_IDENTITY = /\b\w*(?:[eE]mail|[nN]ame)\b\s*(?:!==|===)\s*|\s(?:!==|===)\s*\w*(?:[eE]mail|[nN]ame)\b/

function specs(directory) {
    const found = []
    for (const entry of readdirSync(directory)) {
        const path = join(directory, entry)
        if (statSync(path).isDirectory()) {
            // The fixtures are where the cast is settled, which is the one place both are allowed.
            if (entry !== 'fixtures' && entry !== 'results' && entry !== 'report' && entry !== '.auth') {
                found.push(...specs(path))
            }
            continue
        }
        if (entry.endsWith('.e2e.ts')) found.push(path)
    }
    return found
}

for (const path of specs(E2E)) {
    const lines = readFileSync(path, 'utf-8').split('\n')
    lines.forEach((line, index) => {
        if (line.trimStart().startsWith('*') || line.trimStart().startsWith('//')) return
        const at = `${path}`
        if (DISCOVERY.test(line)) {
            reporter[asError ? 'error' : 'warn'](
                at,
                index + 1,
                'A story works out who to act as. The cast settles that at global setup: read it from '
                + 'fixtures/cast.ts instead.',
                CATEGORY,
            )
        }
        if (MUTABLE_IDENTITY.test(line)) {
            reporter[asError ? 'error' : 'warn'](
                at,
                index + 1,
                'An identity is compared by address or name, which the stories rewrite. Compare the id.',
                CATEGORY,
            )
        }
    })
}

reporter.print()

#!/usr/bin/env node
/**
 * Finds screens that answer a failure with "something went wrong".
 *
 * `common.error` reads "Das hat nicht funktioniert. Versuche es noch einmal, und melde es bitte,
 * wenn es dabei bleibt." It does not say what failed, it does not say whether the reader mistyped
 * something or Ember fell over, and the one instruction it gives is not attached to any way of
 * following it. A reader who reads it twice has learned nothing the second time.
 *
 * There is no need for it. `util/failure.ts` reads a thrown thing and says what kind of failure it
 * was, what to do about it, and whether it looks like a fault in Ember rather than something the
 * reader or their station can put right; `FailureAlert` renders all three and offers a report where
 * it is ours. A `catch` that writes the generic sentence is throwing away everything the server
 * said in order to say nothing.
 *
 * So the sentence is refused wherever a screen might reach for it. It stays in the translations
 * because a fallback of last resort is still worth having, and `describeFailure` is where that last
 * resort lives now.
 */

import {readFileSync} from 'fs'
import {SRC, createReporter, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Generic error'

/** What to do instead, named in every report so nobody has to go looking. */
const REMEDY = 'describe the failure: describeFailure(e, t) from util/failure.ts, rendered with FailureAlert'

/** The sentence itself, however it is reached for. */
const GENERIC = /\bt\(\s*(['"])common\.error\1\s*\)/

/**
 * The one file that may name the sentence, because its job is to recognise it.
 *
 * <p>`FailureAlert` offers a report exactly where a screen could say nothing better than "that did
 * not work", and it decides that by comparing against the sentence itself. It is reading the key
 * rather than answering with it, which is the opposite of what this rule is about.
 */
const OWNS_THE_SENTENCE = 'src/components/feedback/FailureAlert.vue'

/**
 * Where the rule applies: the screens and the things they are built from.
 *
 * <p>Not the whole tree. `util/failure.ts` names the key as the last resort it falls back to, and
 * the translations have to go on carrying it, so both would be reported for doing their job.
 */
function inScope(file) {
    const path = rel(file)
    if (path === OWNS_THE_SENTENCE) return false
    return path.startsWith('src/views/')
        || path.startsWith('src/components/')
        || path.startsWith('src/composables/')
}

/** Every line reaching for the generic sentence, with its number. */
export function genericErrorLines(content) {
    return content
        .split('\n')
        .map((line, index) => ({line: index + 1, text: line}))
        .filter(({text}) => GENERIC.test(text))
}

function run() {
    let checked = 0
    for (const file of walk(SRC, '.vue').concat(walk(SRC, '.ts'))) {
        if (!inScope(file)) continue
        checked++
        for (const {line} of genericErrorLines(readFileSync(file, 'utf-8'))) {
            reporter.error(rel(file), line, `answers a failure with "something went wrong". ${REMEDY}`, CATEGORY)
        }
    }

    console.log(`Checked every screen for failures answered with nothing: ${checked} read.`)
    reporter.print()
    process.exit(reporter.errors.length > 0 ? 1 : 0)
}

if (process.argv[1] && process.argv[1].endsWith('lint-generic-errors.mjs')) run()

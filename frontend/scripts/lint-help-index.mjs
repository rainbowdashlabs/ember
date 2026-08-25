#!/usr/bin/env node
/**
 * Help Centre Search Index Linter
 *
 * The index is generated from the pages tree, and the generated file is committed so the application
 * does not have to read the tree at runtime. This checks the two agree: a page written since the last
 * run, or an article whose keys moved, has to be regenerated rather than noticed later by a reader who
 * searched for something and found nothing.
 *
 * It also names an article no page mounts. That text is in no index and on no screen, and the hand-kept
 * list used to hide the fact by hanging it on a neighbouring page.
 *
 * Exit code 1 when the committed file is stale or an article is unreachable.
 */

import {readFileSync, existsSync} from 'fs'
import {join} from 'path'
import {renderHelpPages, TARGET} from './generate-help-index.mjs'
import {collectHelpPages, mountedArticles} from './help-index.mjs'
import {walk, SRC, RED, GREEN, YELLOW, RESET, BOLD} from './lint-utils.mjs'

let failed = false

const committed = existsSync(TARGET) ? readFileSync(TARGET, 'utf-8') : ''
if (committed !== renderHelpPages()) {
    console.log(`${RED}${BOLD}The help centre search index is out of date.${RESET}`);
    console.log(`  Run ${BOLD}./toolchain.sh fe-help-index${RESET} and commit what it writes.`)
    failed = true
}

const pages = collectHelpPages()
const silent = pages.filter(page => page.prefixes.length === 0)
for (const page of silent) {
    console.log(`${RED}error${RESET} ${page.path}: the article this page mounts draws on no help text`)
    failed = true
}

const mounted = mountedArticles()
const orphans = walk(join(SRC, 'views', 'helpcenter'), '.vue')
    .filter(file => !mounted.has(file))
    .filter(file => readFileSync(file, 'utf-8').includes('helpCenter.'))
    .map(file => file.replace(`${SRC}/`, ''))
for (const orphan of orphans) {
    console.log(`${YELLOW}warning${RESET} ${orphan}: no page mounts this article, so nothing shows its text`)
}

if (!failed) {
    console.log(`${GREEN}The help centre search index matches the pages${RESET} (${pages.length} pages, ${orphans.length} unmounted article(s))`)
}
process.exit(failed ? 1 : 0)

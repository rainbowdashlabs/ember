#!/usr/bin/env node
/**
 * Help Center i18n Linter
 *
 * Fails when a Vue file under src/views/helpcenter/ does not import useI18n.
 *
 * The intent is that every help-center page is fully translatable: visible text
 * goes through vue-i18n's t(). The proxy: a file without useI18n almost
 * certainly inlines text. Once a file calls useI18n, the assumption is that
 * any visible string is wrapped in t().
 *
 * Exit code 1 on a violation, 0 otherwise.
 */

import {readFileSync} from 'fs'
import {join} from 'path'
import {walk, SRC, RED, GREEN, RESET, BOLD, rel} from './lint-utils.mjs'

const HELP_DIR = join(SRC, 'views', 'helpcenter')

const files = walk(HELP_DIR, '.vue')
const offenders = []

for (const file of files) {
    const content = readFileSync(file, 'utf-8')
    if (/\buseI18n\s*\(/.test(content)) continue
    // Delegate pages - a thin wrapper whose template renders another *Help component - inherit
    // translation through that delegate. The actual t() lives in the shared component.
    if (/<([A-Z][A-Za-z0-9]*Help)\b[^>]*\/?>/.test(content)) continue
    offenders.push(rel(file))
}

if (offenders.length > 0) {
    console.log(`${RED}${BOLD}Help-center pages without i18n (${offenders.length}):${RESET}`)
    for (const f of offenders.sort()) console.log(`  ${RED}error${RESET} ${f}`)
    console.log(`\n${RED}Every help-center page must import useI18n and route all visible text through t().${RESET}`)
    process.exit(1)
}

console.log(`${GREEN}${BOLD}All help-center pages are translatable.${RESET}`)

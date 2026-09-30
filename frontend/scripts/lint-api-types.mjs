#!/usr/bin/env node
/**
 * API Types Linter
 *
 * The wire types are generated from the API description the backend writes, and the generated file is
 * committed so the frontend builds without Java. This checks the two agree: a backend change whose
 * description was regenerated but whose types were not has to be regenerated here rather than noticed
 * later as a field the screen reads and the backend no longer sends.
 *
 * Exit code 1 when the committed file is stale.
 */

import {readFileSync, existsSync} from 'fs'
import {renderApiTypes, TARGET} from './generate-api-types.mjs'
import {RED, GREEN, RESET, BOLD} from './lint-utils.mjs'

const committed = existsSync(TARGET) ? readFileSync(TARGET, 'utf-8') : ''
if (committed !== await renderApiTypes()) {
    console.log(`${RED}${BOLD}The generated API types are out of date.${RESET}`)
    console.log(`  Run ${BOLD}./toolchain.sh api-types${RESET} and commit what it writes.`)
    process.exit(1)
}
console.log(`${GREEN}The generated API types match the API description${RESET}`)

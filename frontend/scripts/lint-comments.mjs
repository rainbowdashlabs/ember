#!/usr/bin/env node
/**
 * Comment Linter
 *
 * Code documents itself in this project. Clear names, small functions and honest
 * structure carry the meaning, and a passage that needs a comment beside it to be
 * understood wants rewriting rather than annotating.
 *
 * So only doc comments are allowed: the block that introduces a class, a method,
 * a function, a record or a constant, and says what it is for. Everything else
 * goes. Where a rationale genuinely has to be written down it belongs in the doc
 * comment of the thing it is about, where the next reader will actually look.
 *
 * The one exception is a TODO marking a real gap: something left unimplemented or
 * knowingly missing. Those are better said than left silent.
 *
 * The rule was written down long before this check existed, and it was broken
 * again in every sitting, which is what a rule nobody checks is worth. The Java
 * sources are scanned alongside the frontend sources, since the rule is the
 * project's and not the frontend's.
 *
 * Exit code 1 if a comment is found that is neither a doc comment nor a TODO.
 */

import {readFileSync, statSync} from 'fs'
import {join} from 'path'
import {SRC, walk, GREEN, RESET, BOLD, createReporter} from './lint-utils.mjs'

const reporter = createReporter()


const FRONTEND_EXTENSIONS = ['.ts', '.vue', '.js', '.mjs']

const ROOT_TARGETS = [
    {path: 'src/main/java', extensions: ['.java']},
    {path: 'src/test/java', extensions: ['.java']},
    {path: 'frontend/e2e', extensions: FRONTEND_EXTENSIONS},
    {path: 'frontend/scripts', extensions: FRONTEND_EXTENSIONS},
]

const REPO_ROOT = new URL('../..', import.meta.url).pathname

/**
 * A line that opens a block comment which is a doc comment, and so may stand.
 *
 * Java and TypeScript spell it the same way, and a licence header is a block
 * comment at the top of the file that every source here carries.
 */
const DOC_OPENER = /^\s*\/\*\*/
const BLOCK_OPENER = /^\s*\/\*/
const LICENCE_MARKER = /SPDX-License-Identifier/

const TODO = /^\s*(\/\/|\/\*|\*)\s*TODO\b/

/** A Java text block's delimiter, inside which two slashes are text, such as an address. */
const TEXT_BLOCK = '"""'

/** Where a comment opens in a Vue template, which is markup and holds no doc comments at all. */
const TEMPLATE_COMMENT = /<!--/

/**
 * Whether a `//` sits inside a string or a regular expression rather than
 * starting a comment.
 *
 * Walking the line character by character is the only way to tell: an address in
 * a string, a protocol in a template literal and a character class in a regular
 * expression all carry two slashes and none of them is a comment.
 */
function commentStart(line) {
    let quote = null
    for (let i = 0; i < line.length; i++) {
        const c = line[i]
        if (quote) {
            if (c === '\\') i++
            else if (c === quote) quote = null
            continue
        }
        if (c === '\\') {
            i++
            continue
        }
        if (c === '"' || c === "'" || c === '`') {
            quote = c
            continue
        }
        if (c === '/' && line[i + 1] === '/') return i
        if (c === '/' && line[i + 1] === '*') return i
    }
    return -1
}

function check(file) {
    const lines = readFileSync(file, 'utf-8').split('\n')
    const found = []
    const markup = file.endsWith('.vue')
    const java = file.endsWith('.java')
    let inBlock = false
    let inTemplateComment = false
    let inTextBlock = false
    let inCode = !markup

    for (let i = 0; i < lines.length; i++) {
        let line = lines[i]

        if (inTextBlock) {
            const end = line.indexOf(TEXT_BLOCK)
            if (end === -1) continue
            inTextBlock = false
            line = line.slice(end + TEXT_BLOCK.length)
        }

        if (java) {
            const open = line.indexOf(TEXT_BLOCK)
            if (open !== -1 && line.indexOf(TEXT_BLOCK, open + TEXT_BLOCK.length) === -1) {
                inTextBlock = true
                line = line.slice(0, open)
            }
        }

        if (inTemplateComment) {
            if (line.includes('-->')) inTemplateComment = false
            continue
        }

        if (markup && /^\s*<(script|style)\b/.test(line)) {
            inCode = true
            continue
        }
        if (markup && /^\s*<\/(script|style)>/.test(line)) {
            inCode = false
            continue
        }

        if (markup && !inCode && TEMPLATE_COMMENT.test(line)) {
            inTemplateComment = !line.slice(line.indexOf('<!--')).includes('-->')
            found.push({line: i + 1, message: 'Template comment. Name the block with a component or a class instead.'})
            continue
        }

        if (inBlock) {
            if (line.includes('*/')) inBlock = false
            continue
        }

        if (!inCode) continue

        if (TODO.test(line)) continue

        if (DOC_OPENER.test(line)) {
            inBlock = !line.includes('*/')
            continue
        }

        if (BLOCK_OPENER.test(line) || LICENCE_MARKER.test(line)) {
            const licence = LICENCE_MARKER.test(line) || LICENCE_MARKER.test(lines[i + 1] ?? '')
            inBlock = !line.includes('*/')
            if (!licence) {
                found.push({
                    line: i + 1,
                    message: 'Block comment that is not a doc comment. Put the reasoning in the doc comment of'
                        + ' the class or function it belongs to, or rewrite the passage so it needs none.',
                })
            }
            continue
        }

        const start = commentStart(line)
        if (start === -1) continue
        if (line.slice(start).startsWith('/*')) {
            inBlock = !line.slice(start).includes('*/')
            found.push({line: i + 1, message: 'Inline block comment. Only doc comments are allowed.'})
            continue
        }
        found.push({
            line: i + 1,
            message: `Line comment at column ${start + 1}. Only doc comments and TODO markers are allowed;`
                + ' put the reasoning in the doc comment of the enclosing class or function.',
        })
    }

    for (const one of found) {
        reporter.error(file, one.line, one.message)
    }

}

/** The generated API types, whose comments are the generator's and say what the backend declares. */
const GENERATED = join(SRC, 'api', 'generated')

const scanned = []
for (const extension of FRONTEND_EXTENSIONS) {
    scanned.push(...walk(SRC, extension).filter(file => !file.startsWith(GENERATED)))
}
scanned.forEach(check)

let rootFiles = 0
for (const target of ROOT_TARGETS) {
    const absolute = join(REPO_ROOT, target.path)
    const files = statSync(absolute).isDirectory()
        ? target.extensions.flatMap(extension => walk(absolute, extension))
        : [absolute]
    files.forEach(check)
    rootFiles += files.length
}

if (reporter.errors.length === 0 && reporter.warnings.length === 0) {
    console.log(
        `\n${GREEN}${BOLD}Comment lint passed.${RESET} ${scanned.length + rootFiles} files checked.\n`,
    )
} else {
    reporter.print()
    reporter.exit()
}

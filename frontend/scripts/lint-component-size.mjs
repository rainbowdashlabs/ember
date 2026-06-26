#!/usr/bin/env node
/**
 * Block-size and div-density linter for the Ember frontend.
 *
 * Two checks live here. Both target views (and exempt src/components/) because
 * the components dir is where the extracted pieces are supposed to land.
 *
 * 1. Block size: any single template block (element + its children) that
 *    spans more than --warn=N lines (default 40) is a hint that the inner
 *    content wants its own component. The check warns at --warn=N and errors
 *    at --error=N (default 80).
 *
 * 2. Section density: any element on the 2nd or 3rd template level that has
 *    many direct "section" children — plain <div>, semantic HTML containers
 *    (<section>, <article>, <header>, <footer>, <main>, <nav>, <aside>), or
 *    Ember container components (NeutralContainer, PrimaryContainer, …) —
 *    is treated as a stack of distinct sections that each want their own
 *    component. Warns at --div-warn=N (default 4) direct section children,
 *    errors at --div-error=N (default 6).
 *
 * What is exempt from the block-size check:
 *   - files inside src/components/ (those are the targets for extraction)
 *   - the root <template> element
 *   - common outer wrappers: ViewContent, ViewLayout, SidebarLayout, NuxtPage,
 *     NuxtLayout, HelpArticle, Modal — these define the page shell and the
 *     entire body lives inside them
 *   - void HTML elements (br, hr, img, input, …)
 *   - self-closing tags
 */

import {readFileSync} from 'fs'
import {SRC, walk, extractTemplate, isInsideComponents, createReporter} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter
const CAT_BLOCK = 'Block size'
const CAT_DIV = 'Section density'

const SECTION_TAGS = new Set([
    'div', 'section', 'article', 'header', 'footer', 'main', 'nav', 'aside',
    'NeutralContainer', 'PrimaryContainer', 'SecondaryContainer',
    'SuccessContainer', 'ErrorContainer', 'InfoContainer', 'BaseContainer',
])

function isSectionTag(tag) {
    return SECTION_TAGS.has(tag) || SECTION_TAGS.has(tag.toLowerCase())
}

const args = new Map()
for (const arg of process.argv.slice(2)) {
    const m = arg.match(/^--([\w-]+)=(.+)$/)
    if (m) args.set(m[1], m[2])
}

const BLOCK_WARN = Number(args.get('warn') ?? process.env.LINT_BLOCK_WARN ?? 30)
const BLOCK_ERROR = Number(args.get('error') ?? process.env.LINT_BLOCK_ERROR ?? 50)
const DIV_WARN = Number(args.get('div-warn') ?? process.env.LINT_DIV_WARN ?? 4)
const DIV_ERROR = Number(args.get('div-error') ?? process.env.LINT_DIV_ERROR ?? 6)

const ROOT_WRAPPER_TAGS = new Set([
    'template',
    'ViewContent',
    'ViewLayout',
    'SidebarLayout',
    'NuxtPage',
    'NuxtLayout',
    'NuxtRoot',
    'HelpArticle',
    'Modal',
])

const VOID_TAGS = new Set([
    'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
    'link', 'meta', 'param', 'source', 'track', 'wbr',
])

const TAG_REGEX = /<(\/?)([A-Za-z][\w-]*)([^>]*?)(\/?)>/g

function lineOfIndex(template, idx) {
    let line = 1
    for (let i = 0; i < idx; i++) {
        if (template.charCodeAt(i) === 10) line++
    }
    return line
}

function reportBlock(file, tag, openLine, span) {
    if (span >= BLOCK_ERROR) {
        error(file, openLine, `<${tag}> block spans ${span} lines (>= ${BLOCK_ERROR}). Extract to a component.`, CAT_BLOCK)
    } else if (span >= BLOCK_WARN) {
        warn(file, openLine, `<${tag}> block spans ${span} lines (>= ${BLOCK_WARN}). Consider extracting to a component.`, CAT_BLOCK)
    }
}

function reportSectionDensity(file, parentTag, openLine, depth, count) {
    const msg = `<${parentTag}> at level ${depth} has ${count} direct section children (div / NeutralContainer / section / …) — each section likely wants its own component.`
    if (count >= DIV_ERROR) {
        error(file, openLine, msg, CAT_DIV)
    } else if (count >= DIV_WARN) {
        warn(file, openLine, msg, CAT_DIV)
    }
}

const vueFiles = walk(SRC, '.vue').filter(f => !isInsideComponents(f))

for (const file of vueFiles) {
    const content = readFileSync(file, 'utf-8')
    const template = extractTemplate(content)
    if (!template) continue
    const templateStartLine = content.substring(0, content.indexOf('<template>')).split('\n').length

    const stack = []

    for (const m of template.matchAll(TAG_REGEX)) {
        const [, slash, tag, attrs, selfSlash] = m
        if (VOID_TAGS.has(tag.toLowerCase())) continue
        const closeMatch = slash === '/'
        const selfClosing = selfSlash === '/' || /\/$/.test(attrs.trim())
        if (selfClosing && !closeMatch) continue

        const idxInTemplate = m.index ?? 0
        const tagLine = templateStartLine + lineOfIndex(template, idxInTemplate) - 1

        if (!closeMatch) {
            const depth = stack.length + 1
            if (stack.length > 0 && isSectionTag(tag)) {
                stack[stack.length - 1].sectionChildren++
            }
            stack.push({tag, line: tagLine, depth, sectionChildren: 0})
            continue
        }

        for (let i = stack.length - 1; i >= 0; i--) {
            if (stack[i].tag === tag) {
                const opened = stack[i]
                stack.length = i
                const span = tagLine - opened.line + 1
                const skipBlock = ROOT_WRAPPER_TAGS.has(opened.tag) || opened.depth <= 2
                if (!skipBlock) reportBlock(file, opened.tag, opened.line, span)
                if ((opened.depth === 2 || opened.depth === 3) && opened.sectionChildren >= DIV_WARN) {
                    reportSectionDensity(file, opened.tag, opened.line, opened.depth, opened.sectionChildren)
                }
                break
            }
        }
    }
}

console.log(
    `\n\x1b[1mTemplate Structure Check\x1b[0m`
    + ` (block warn >= ${BLOCK_WARN} / error >= ${BLOCK_ERROR},`
    + ` section-density warn >= ${DIV_WARN} / error >= ${DIV_ERROR})`,
)
reporter.print()
reporter.exit()

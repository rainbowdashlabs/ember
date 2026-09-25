#!/usr/bin/env node
/**
 * Finds server-rendered public pages that say nothing about themselves.
 *
 * A page rendered on the server is read by a search engine and by whatever draws the preview card
 * beside a pasted link, and both of them read the head and nothing else. A page that sets no title,
 * no description and no OpenGraph tags is therefore indexed as a blank and previewed as a blank,
 * which is what happened to a station's public calendar: the shell around it described the station,
 * so every one of its pages unfurled as the same nameless card.
 *
 * The rule only reaches pages where it can do any good. A page behind a sign-in has no preview
 * worth drawing, and a page in an area the configuration renders in the browser reaches no crawler
 * at all, so neither is asked for anything.
 *
 * Which pages those are is worked out rather than listed, from the two files that already decide
 * it. `nuxt.config.ts` names the route prefixes this project deliberately renders on the server,
 * and `src/middleware/auth.global.ts` names the paths that are let through without a session. A
 * page under both is one a stranger can open and a crawler can read, and it has to describe itself.
 *
 * What it must carry is a title, a description, an OpenGraph title and description, and a Twitter
 * card, whether written out by hand or built with `socialMeta()`. What it may leave out is
 * everything, when it draws nothing: a page whose whole job is to forward the reader somewhere else
 * has nothing of its own to describe, and is left to the shell around it.
 *
 * It also refuses a head that is filled in too late. Tags written from data a loader fetches when
 * the component mounts are empty in the page the server sends, so they reach a crawler as nothing
 * and a link preview as nothing, which is the same failure one step further along.
 *
 * It fails the build. Every page it covers was given its meta in the change that introduced it, so
 * there is no backlog to print and anything it finds from here is new.
 */

import {existsSync, readFileSync} from 'fs'
import {join, relative, sep} from 'path'
import {PAGES_DIR, SRC, createReporter, extractTemplate, rel, walk} from './lint-utils.mjs'

const reporter = createReporter()
const CATEGORY = 'Social meta'

/** What to do about it, named in every report so nobody has to go looking. */
const REMEDY = 'give the page its own title and description and build the tags with socialMeta() from src/util/socialMeta.ts'

/** The same, for a head that is written late rather than not at all. */
const LATE_REMEDY = 'fetch it with useAsyncData, as PublicStationShell.vue does, so the page the server sends already carries it'

/**
 * The loaders that only run once the browser has taken over.
 *
 * <p>Both hang their work off the component being mounted, which is something the server never does.
 */
const LATE_LOADERS = ['onMounted', 'useAsyncLoader']

const REF_READ = /\b([A-Za-z_$][\w$]*)\??\.value\b/g
const REF_WRITE = /\b([A-Za-z_$][\w$]*)\.value\s*=[^=]/g
const CALLED = /\b([A-Za-z_$][\w$]*)\s*\(/g

const NUXT_CONFIG = join(SRC, '..', 'nuxt.config.ts')
const AUTH_MIDDLEWARE = join(SRC, 'middleware', 'auth.global.ts')

const BUILDER = /\bsocialMeta\s*\(/
const DESCRIPTION = /name:\s*'description'/
const OG_TITLE = /'og:title'/
const OG_DESCRIPTION = /'og:description'/
const TWITTER_CARD = /'twitter:card'/
const TWITTER_TITLE = /'twitter:title'/

/**
 * The text between one pair of delimiters, counting the nested ones on the way.
 *
 * @param source the text to read
 * @param from   where to start looking for the opening delimiter
 * @param open   the opening delimiter
 * @param close  the closing delimiter
 * @return what stands between them, or an empty string where they do not close
 */
function delimited(source, from, open, close) {
    const start = source.indexOf(open, from)
    if (start === -1) return ''
    let depth = 0
    for (let i = start; i < source.length; i++) {
        if (source[i] === open) depth++
        else if (source[i] === close) {
            depth--
            if (depth === 0) return source.slice(start + 1, i)
        }
    }
    return ''
}

/**
 * The route prefixes the configuration deliberately renders on the server.
 *
 * <p>Read from the `routeRules` that say so outright rather than from what Nuxt would do by
 * default, because the statement is what matters: an area written down as server-rendered is one
 * somebody meant a crawler to read, and an area that has never made that statement is not.
 *
 * @param nuxtConfig the contents of `nuxt.config.ts`
 */
export function serverRenderedRoutes(nuxtConfig) {
    const block = delimited(nuxtConfig, nuxtConfig.indexOf('routeRules:'), '{', '}')
    const found = []
    for (const [, path, body] of block.matchAll(/'([^']+)':\s*\{([^}]*)\}?/g)) {
        if (/\bssr:\s*true\b/.test(body)) found.push(path)
    }
    return found
}

/**
 * The paths a reader with no session is let through to.
 *
 * <p>Two shapes, both taken from the one middleware that decides it. The list it tests with
 * `startsWith` gives the prefixes; an early `return` on a path of its own gives the exact ones. A
 * gate that compares a path and then does something other than return is not a way through, which
 * is what tells the administration and station gates further down apart from these.
 *
 * @param middleware the contents of the global auth middleware
 */
export function signInFreeRoutes(middleware) {
    const list = delimited(middleware, middleware.indexOf('publicPaths'), '[', ']')
    const prefixes = [...list.matchAll(/'([^']+)'/g)].map(entry => entry[1])
    const exact = []
    for (const line of middleware.split('\n')) {
        const guard = line.match(/^\s*if \((.*)\) return\s*$/)
        if (!guard) continue
        for (const [, path] of guard[1].matchAll(/to\.path\s*===\s*'([^']+)'/g)) exact.push(path)
        for (const [, path] of guard[1].matchAll(/to\.path\.startsWith\('([^']+)'\)/g)) prefixes.push(path)
    }
    return {prefixes, exact}
}

/** Whether a route is covered by one rule of the configuration, whose `**` reaches everything below it. */
function coveredBy(route, rule) {
    if (rule.endsWith('/**')) return route.startsWith(rule.slice(0, -2))
    return route === rule
}

/** The address a page file answers on, spelled the way the middleware sees it. */
function routeOf(file) {
    const withoutExtension = relative(PAGES_DIR, file).split(sep).join('/').replace(/\.vue$/, '')
    const trimmed = withoutExtension.replace(/(^|\/)index$/, '')
    const segments = trimmed.split('/').filter(segment => segment.length > 0).map(segment => {
        if (segment.startsWith('[...')) return ':pathMatch'
        if (segment.startsWith('[[')) return `:${segment.slice(2, -2)}`
        if (segment.startsWith('[')) return `:${segment.slice(1, -1)}`
        return segment
    })
    return `/${segments.join('/')}`
}

/** Whether a page is one a stranger can open, by its address or by saying so itself. */
function opensWithoutSignIn(route, source, {prefixes, exact}) {
    const meta = source.match(/definePageMeta\(\{([\s\S]*?)\}\)/)
    if (meta && /\bpublic:\s*true\b/.test(meta[1])) return true
    if (exact.includes(route)) return true
    return prefixes.some(prefix => route === prefix || route.startsWith(`${prefix}/`))
}

/** The views a page renders, read from its own imports. */
function viewsOf(pageSource) {
    const sources = []
    for (const [, spec] of pageSource.matchAll(/from\s+'[~@]\/(views\/[^']+\.vue)'/g)) {
        const file = join(SRC, spec)
        if (existsSync(file)) sources.push(readFileSync(file, 'utf-8'))
    }
    return sources
}

/**
 * Whether a component puts nothing on the screen.
 *
 * <p>A component with no words, no values and no other component in its template draws nothing a
 * reader could be told about, which is what a page that only forwards somebody else looks like. It
 * is the one thing this check lets past without a description, because inventing one for it would
 * mean describing a page nobody ever sees.
 */
export function rendersNothing(content) {
    const template = extractTemplate(content)
    if (!template) return true
    const inner = template.replace(/^<template>/, '').replace(/<\/template>$/, '')
    if (/\{\{/.test(inner)) return false
    if (/<[A-Z][\w-]*|<[a-z]+-[\w-]+/.test(inner)) return false
    return inner.replace(/<[^>]*>/g, '').trim().length === 0
}

/**
 * The source with everything that is prose blanked out, keeping every position where it was.
 *
 * <p>Brackets are counted to find where a head ends, and a bracket written inside a sentence closes
 * nothing. Nor does the apostrophe in "the station's name", which would otherwise read as the start
 * of a string and swallow the code after it, so the comments go first and the strings second.
 * Blanking rather than removing keeps the offsets the search has already found.
 */
function blankProse(source) {
    return source
        .replace(/\/\*[\s\S]*?\*\//g, match => ' '.repeat(match.length))
        .replace(/(^|[^:"'`\w])\/\/[^\n]*/g, (match, before) => before + ' '.repeat(match.length - before.length))
        .replace(/'(?:\\.|[^'\\\n])*'|"(?:\\.|[^"\\\n])*"|`(?:\\.|[^`\\])*`/g,
            match => match[0].repeat(match.length))
}

/** The head each `useHead` call is given, so a title can be looked for where a title belongs. */
function headBlocks(source) {
    const blanked = blankProse(source)
    return [...blanked.matchAll(/useHead\s*\(/g)].map(call => delimited(blanked, call.index, '(', ')'))
}

/**
 * The same head without the arrays that carry titles of their own.
 *
 * <p>A feed link and a piece of structured data both spell a `title` inside themselves, and neither
 * is the page's. Taking them out first is what keeps a page that offers a feed and nothing else
 * from reading as a page that has named itself.
 */
function withoutNestedArrays(block) {
    let text = block
    for (const key of ['link', 'script']) {
        const at = text.indexOf(`${key}: [`)
        if (at === -1) continue
        text = text.replace(`[${delimited(text, at, '[', ']')}]`, '')
    }
    return text
}

/**
 * What a page fails to tell a crawler about itself.
 *
 * <p>Separated from the walk so the rule itself can be put to a test: what it has to catch and what
 * it has to leave alone are the whole of its value, and neither is obvious from reading it.
 *
 * @param source the page and the views it renders, as one text
 * @return one entry per missing piece, and nothing at all where the page is complete
 */
export function socialMetaGaps(source) {
    const heads = headBlocks(source)
    if (heads.length === 0) return ['sets no head at all']

    const gaps = []
    if (!heads.some(head => /(?:^|[^\w.])title\s*[:,]/.test(withoutNestedArrays(head)))) {
        gaps.push('names no title')
    }
    if (BUILDER.test(source)) return gaps

    if (!DESCRIPTION.test(source)) gaps.push('carries no description')
    if (!OG_TITLE.test(source) || !OG_DESCRIPTION.test(source)) gaps.push('carries no OpenGraph title and description')
    if (!TWITTER_CARD.test(source) || !TWITTER_TITLE.test(source)) gaps.push('carries no Twitter card')
    return gaps
}

/** Every argument list a named function is called with. */
function callArguments(source, name) {
    const calls = [...source.matchAll(new RegExp(`\\b${name}\\s*\\(`, 'g'))]
    return calls.map(call => delimited(source, call.index, '(', ')'))
}

/** The body of a function declared in this file, and nothing where it is declared somewhere else. */
function namedFunctionBody(source, name) {
    const declared = source.search(new RegExp(`function\\s+${name}\\s*\\(`))
    if (declared === -1) return ''
    return delimited(source, source.indexOf(')', declared), '{', '}')
}

/** The values standing on those, which arrive exactly as late as what they are computed from. */
function derivedFrom(source, filled) {
    const derived = []
    for (const found of source.matchAll(/\bconst\s+([A-Za-z_$][\w$]*)\s*=\s*computed\s*\(/g)) {
        const body = delimited(source, found.index + found[0].length - 1, '(', ')')
        if ([...body.matchAll(REF_READ)].some(read => filled.has(read[1]))) derived.push(found[1])
    }
    return derived
}

/** Everything a mount-time loader fills, following the functions it calls one step. */
function filledAfterMount(source) {
    const filled = new Set()
    for (const loader of LATE_LOADERS) {
        for (const body of callArguments(source, loader)) {
            for (const written of body.matchAll(REF_WRITE)) filled.add(written[1])
            for (const called of body.matchAll(CALLED)) {
                for (const written of namedFunctionBody(source, called[1]).matchAll(REF_WRITE)) filled.add(written[1])
            }
        }
    }
    for (const derived of derivedFrom(source, filled)) filled.add(derived)
    return filled
}

/**
 * What a page's head reads that will not be there in time for anybody to read it.
 *
 * <p>A head is worth nothing to a crawler or to a chat client unfurling a link unless it stands in
 * the page the server sends, and a value that a loader fills when the component mounts is a value
 * the server never had. This is the same failure as writing no head at all, one step further along:
 * the tags are all present and every one of them is empty.
 *
 * <p>It follows a value one step out of the loader, through a function the loader calls and through
 * a computed standing on what it filled, and no further. A value a composable owns and fills on the
 * view's behalf is beyond it, which is the shape it cannot see.
 *
 * @param source the page and the views it renders, as one text
 * @return the names the head reads too early, in the order they read best
 */
export function lateHeadSources(source) {
    const blanked = blankProse(source)
    const filled = filledAfterMount(blanked)
    if (filled.size === 0) return []

    const read = new Set()
    for (const head of headBlocks(source)) {
        for (const found of head.matchAll(REF_READ)) read.add(found[1])
    }
    return [...filled].filter(name => read.has(name)).sort()
}

function run() {
    const serverRendered = serverRenderedRoutes(readFileSync(NUXT_CONFIG, 'utf-8'))
    const signInFree = signInFreeRoutes(readFileSync(AUTH_MIDDLEWARE, 'utf-8'))

    let checked = 0
    for (const file of walk(PAGES_DIR, '.vue')) {
        const source = readFileSync(file, 'utf-8')
        const route = routeOf(file)
        if (!serverRendered.some(rule => coveredBy(route, rule))) continue
        if (!opensWithoutSignIn(route, source, signInFree)) continue

        const views = viewsOf(source)
        if (views.length > 0 && views.every(rendersNothing)) continue

        checked++
        const chain = [source, ...views].join('\n')
        for (const gap of socialMetaGaps(chain)) {
            reporter.error(rel(file), 0, `${route} ${gap}. ${REMEDY}`, CATEGORY)
        }
        for (const name of lateHeadSources(chain)) {
            reporter.error(rel(file), 0, `${route} builds its head from ${name}, which is filled only once the browser has taken over. ${LATE_REMEDY}`, CATEGORY)
        }
    }

    console.log(`Checked every server-rendered page a stranger can open for social meta: ${checked} pages.`)
    reporter.print()
    process.exit(reporter.errors.length > 0 ? 1 : 0)
}

if (process.argv[1] && process.argv[1].endsWith('lint-social-meta.mjs')) run()

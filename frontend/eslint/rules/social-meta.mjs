import {join, relative, sep} from 'node:path'
import {callOf, findAll, readComponent, readScript, resolveComponent, sourceDirOf, walk} from './sfc.mjs'

/**
 * Finds server-rendered public pages that say nothing about themselves.
 *
 * <p>A page rendered on the server is read by a search engine and by whatever draws the preview
 * card beside a pasted link, and both read the head and nothing else. A page with no title, no
 * description and no OpenGraph tags is indexed as a blank and previewed as a blank.
 *
 * <p>The rule only reaches pages where it can do any good, and works out which from the two files
 * that already decide it: `nuxt.config.ts` names the route prefixes rendered on the server, and
 * `src/util/publicRoute.ts` names the paths let through without a session. A page under both, or
 * one declaring itself public, has to carry a title, a description, an OpenGraph title and
 * description and a Twitter card, written by hand or built with `socialMeta()`. A page whose views
 * draw nothing only forwards the reader and has nothing to describe.
 *
 * <p>It also refuses a head filled in too late: tags built from values a loader fills when the
 * component mounts are empty in the page the server sends. It follows a value one step out of the
 * loader, through a function the loader calls and through a computed standing on what it filled,
 * and no further.
 *
 * <p>It runs on the page file and reads the page together with the views it imports.
 */

const LATE_LOADERS = ['onMounted', 'useAsyncLoader']

/**
 * The name of a property's key, however it is written.
 *
 * @param property a property node
 * @returns the name, or null for a computed key
 */
function keyName(property) {
    if (property.computed) return null
    return property.key.type === 'Identifier' ? property.key.name : property.key.value
}

/**
 * An expression without the `as` and `satisfies` around it.
 *
 * @param node the expression
 * @returns the expression inside
 */
function unwrap(node) {
    let inner = node
    while (inner && (inner.type === 'TSAsExpression' || inner.type === 'TSSatisfiesExpression')) inner = inner.expression
    return inner
}

/**
 * The route prefixes the configuration deliberately renders on the server.
 *
 * @param config the program of `nuxt.config.ts`
 * @returns the keys of `routeRules` whose rule says `ssr: true`
 */
export function serverRenderedRoutes(config) {
    const rules = findAll(config, node => node.type === 'Property' && keyName(node) === 'routeRules')[0]
    if (unwrap(rules?.value)?.type !== 'ObjectExpression') return []
    return unwrap(rules.value).properties
        .filter(rule => rule.type === 'Property' && unwrap(rule.value).type === 'ObjectExpression'
            && unwrap(rule.value).properties.some(setting => setting.type === 'Property' && keyName(setting) === 'ssr'
                && setting.value.type === 'Literal' && setting.value.value === true))
        .map(keyName)
}

/**
 * The paths a reader with no session is let through to.
 *
 * @param routes the program of `src/util/publicRoute.ts`
 * @returns `{prefixes, exact}`
 */
export function signInFreeRoutes(routes) {
    const entries = name => {
        const declarator = findAll(routes, node => node.type === 'VariableDeclarator'
            && node.id.type === 'Identifier' && node.id.name === name)[0]
        const list = unwrap(declarator?.init)
        if (list?.type !== 'ArrayExpression') return []
        return list.elements.filter(element => element?.type === 'Literal' && typeof element.value === 'string')
            .map(element => element.value)
    }
    return {prefixes: entries('PUBLIC_PATH_PREFIXES'), exact: entries('PUBLIC_EXACT_PATHS')}
}

/**
 * Whether a component puts nothing on the screen: no words, no values, no other component.
 *
 * @param template the component's root template element
 * @returns true for a component that draws nothing
 */
export function rendersNothing(template) {
    if (!template) return true
    let draws = false
    walk(template, node => {
        if (node === template) return true
        if (node.type === 'VElement' && (/^[A-Z]/.test(node.rawName) || node.rawName.includes('-'))) draws = true
        if (node.type === 'VText' && node.value.trim() !== '') draws = true
        if (node.type === 'VExpressionContainer' && node.parent?.type === 'VElement') draws = true
        return !draws
    })
    return !draws
}

/**
 * The arguments of every `useHead` call in the page and its views.
 *
 * @param programs the script programs
 * @returns one argument list per call
 */
function headsOf(programs) {
    return programs.flatMap(program => findAll(program, node => node.type === 'CallExpression'
        && node.callee.type === 'Identifier' && node.callee.name === 'useHead')).map(call => call.arguments)
}

/**
 * Whether a head names a title, leaving out the feed links and structured data, which spell a
 * title of their own that is not the page's.
 *
 * @param head the arguments of a `useHead` call
 * @returns true where the head carries a `title`
 */
function namesTitle(head) {
    let found = false
    for (const argument of head) {
        walk(argument, node => {
            if (node.type !== 'Property') return !found
            const name = keyName(node)
            if ((name === 'link' || name === 'script') && node.value.type === 'ArrayExpression') return false
            if (name === 'title') found = true
            return !found
        })
    }
    return found
}

/**
 * Whether any of the programs holds a node that satisfies a test.
 *
 * @param programs the script programs
 * @param test the predicate
 * @returns true where one does
 */
function holds(programs, test) {
    return programs.some(program => findAll(program, test).length > 0)
}

/**
 * Whether the programs hold a string literal of the given value.
 *
 * @param programs the script programs
 * @param value the string
 * @returns true where one of them does
 */
function spells(programs, value) {
    return holds(programs, node => node.type === 'Literal' && node.value === value)
}

/**
 * What a page fails to tell a crawler about itself.
 *
 * @param programs the script programs of the page and the views it renders
 * @returns one entry per missing piece, nothing where the page is complete
 */
export function socialMetaGaps(programs) {
    const heads = headsOf(programs)
    if (heads.length === 0) return ['sets no head at all']

    const gaps = []
    if (!heads.some(namesTitle)) gaps.push('names no title')
    if (holds(programs, node => node.type === 'CallExpression' && node.callee.type === 'Identifier'
        && node.callee.name === 'socialMeta')) return gaps

    const describes = holds(programs, node => node.type === 'Property' && keyName(node) === 'name'
        && node.value.type === 'Literal' && node.value.value === 'description')
    if (!describes) gaps.push('carries no description')
    if (!spells(programs, 'og:title') || !spells(programs, 'og:description')) gaps.push('carries no OpenGraph title and description')
    if (!spells(programs, 'twitter:card') || !spells(programs, 'twitter:title')) gaps.push('carries no Twitter card')
    return gaps
}

/**
 * The name of the state an expression reads through `.value`, as in `entry.value`.
 *
 * @param node a member expression
 * @returns the name, or null
 */
function refName(node) {
    if (node?.type !== 'MemberExpression' || node.computed || node.property.name !== 'value') return null
    return node.object.type === 'Identifier' ? node.object.name : null
}

/**
 * The state a piece of code writes through `.value =`.
 *
 * @param root the code
 * @returns the names written
 */
function refsWritten(root) {
    return findAll(root, node => node.type === 'AssignmentExpression' && node.operator === '=' && refName(node.left))
        .map(node => refName(node.left))
}

/**
 * The state a piece of code reads through `.value`.
 *
 * @param root the code
 * @returns the names read
 */
function refsRead(root) {
    return findAll(root, node => refName(node) !== null).map(refName)
}

/**
 * Everything a mount-time loader fills, following the functions it calls one step and the
 * computed values standing on what it filled.
 *
 * @param programs the script programs
 * @returns the names filled only once the browser has taken over
 */
function filledAfterMount(programs) {
    const functions = programs.flatMap(program => findAll(program, node => node.type === 'FunctionDeclaration' && node.id))
    const filled = new Set()
    for (const program of programs) {
        const loaders = findAll(program, node => node.type === 'CallExpression'
            && node.callee.type === 'Identifier' && LATE_LOADERS.includes(node.callee.name))
        for (const loader of loaders) {
            for (const argument of loader.arguments) {
                refsWritten(argument).forEach(name => filled.add(name))
                const called = findAll(argument, node => node.type === 'CallExpression')
                    .map(call => (call.callee.type === 'Identifier' ? call.callee.name : call.callee.property?.name))
                for (const name of called) {
                    const declared = functions.find(candidate => candidate.id.name === name)
                    if (declared) refsWritten(declared.body).forEach(written => filled.add(written))
                }
            }
        }
    }
    const derived = programs.flatMap(program => findAll(program, node => node.type === 'VariableDeclarator'
        && node.id.type === 'Identifier' && node.init?.type === 'CallExpression'
        && node.init.callee.type === 'Identifier' && node.init.callee.name === 'computed'
        && refsRead(node.init).some(name => filled.has(name)))).map(node => node.id.name)
    derived.forEach(name => filled.add(name))
    return filled
}

/**
 * What a page's head reads that will not be there in time for anybody to read it.
 *
 * @param programs the script programs of the page and the views it renders
 * @returns the names the head reads too early, sorted
 */
export function lateHeadSources(programs) {
    const filled = filledAfterMount(programs)
    if (filled.size === 0) return []
    const read = new Set(headsOf(programs).flatMap(head => head.flatMap(refsRead)))
    return [...filled].filter(name => read.has(name)).sort()
}

/**
 * The address a page file answers on, spelled the way the middleware sees it.
 *
 * @param pagesDir the `pages` directory
 * @param file the page file
 * @returns the route
 */
function routeOf(pagesDir, file) {
    const trimmed = relative(pagesDir, file).split(sep).join('/').replace(/\.vue$/, '').replace(/(^|\/)index$/, '')
    const segments = trimmed.split('/').filter(segment => segment.length > 0).map(segment => {
        if (segment.startsWith('[...')) return ':pathMatch'
        if (segment.startsWith('[[')) return `:${segment.slice(2, -2)}`
        if (segment.startsWith('[')) return `:${segment.slice(1, -1)}`
        return segment
    })
    return `/${segments.join('/')}`
}

/**
 * Whether a route is covered by one rule of the configuration, whose `**` reaches everything below it.
 *
 * @param route the route
 * @param rule the configured prefix
 * @returns true where it is
 */
function coveredBy(route, rule) {
    if (rule.endsWith('/**')) return route.startsWith(rule.slice(0, -2))
    return route === rule
}

/**
 * Whether a page is one a stranger can open, by its address or by declaring itself public.
 *
 * @param route the route
 * @param program the page's script program
 * @param routes the paths let through without a session
 * @returns true where it is
 */
function opensWithoutSignIn(route, program, {prefixes, exact}) {
    const meta = callOf(program, 'definePageMeta')?.arguments[0]
    const declared = meta?.properties?.some(property => property.type === 'Property' && keyName(property) === 'public'
        && property.value.type === 'Literal' && property.value.value === true)
    if (declared || exact.includes(route)) return true
    return prefixes.some(prefix => route === prefix || route.startsWith(`${prefix}/`))
}

export default {
    meta: {
        type: 'problem',
        docs: {description: 'A server-rendered page a stranger can open describes itself in its head, in time.'},
        schema: [],
        messages: {
            gap: '{{route}} {{gap}}. Give the page its own title and description and build the tags with socialMeta() from src/util/socialMeta.ts.',
            late: '{{route}} builds its head from {{name}}, which is filled only once the browser has taken over. Fetch it with useAsyncData, as PublicStationShell.vue does, so the page the server sends already carries it.',
            unreadable: 'Cannot read {{file}}, which says which pages are rendered on the server and open without a session.',
        },
    },
    create(context) {
        return {
            'Program:exit'(program) {
                const srcDir = sourceDirOf(context.filename)
                if (!srcDir) return
                const configFile = join(srcDir, '..', 'nuxt.config.ts')
                const routesFile = join(srcDir, 'util', 'publicRoute.ts')
                const config = readScript(configFile)
                const routes = readScript(routesFile)
                const loc = {line: 1, column: 0}
                if (!config || !routes) {
                    context.report({loc, messageId: 'unreadable', data: {file: config ? routesFile : configFile}})
                    return
                }

                const route = routeOf(join(srcDir, 'pages'), context.filename)
                if (!serverRenderedRoutes(config).some(rule => coveredBy(route, rule))) return
                if (!opensWithoutSignIn(route, program, signInFreeRoutes(routes))) return

                const views = program.body
                    .filter(statement => statement.type === 'ImportDeclaration' && /^[~@]\/views\/.+\.vue$/.test(statement.source.value))
                    .map(statement => readComponent(resolveComponent(statement.source.value, context.filename, srcDir)))
                    .filter(Boolean)
                if (views.length > 0 && views.every(view => rendersNothing(view.template))) return

                const programs = [program, ...views.map(view => view.script)]
                for (const gap of socialMetaGaps(programs)) {
                    context.report({loc, messageId: 'gap', data: {route, gap}})
                }
                for (const name of lateHeadSources(programs)) {
                    context.report({loc, messageId: 'late', data: {route, name}})
                }
            },
        }
    },
}

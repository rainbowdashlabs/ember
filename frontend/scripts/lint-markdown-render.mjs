/**
 * Keeps every string that reaches a `v-html` binding on a sanitised path.
 *
 * `marked` passes raw HTML through by design, so markdown parsed anywhere other than the shared
 * renderer in `src/util/markdown.ts` is stored cross-site scripting waiting for a reader. Two rules
 * follow from that: nothing but the renderer imports `marked`, and a `v-html` binding either shows
 * what the renderer produced or is declared here with the reason it is safe.
 *
 * The declared sites are the ones a person has checked: HTML the backend already sanitised, search
 * snippets built from stripped markup, and constant text written in the file itself. An entry that
 * no longer matches a binding is reported, so the list cannot quietly outlive what it describes.
 */

import {readFileSync} from 'fs'
import {createReporter, extractTemplate, rel, SRC, walk} from './lint-utils.mjs'

const reporter = createReporter()
const {warn, error} = reporter

const CAT_MARKED = 'marked'
const CAT_BINDING = 'v-html'
const CAT_STALE = 'stale'

const RENDERER = 'util/markdown.ts'
const RENDER_CALLS = ['renderMarkdown', 'renderPageMarkdown']

/**
 * Bindings that do not come from the shared renderer and are safe for a reason that has been
 * checked. The reason is printed when the entry goes stale, so it has to say why rather than that.
 */
const DECLARED = new Map([
    ['views/public/publickbfileview/KbFileRenderer.vue', 'knowledge base HTML rendered and sanitised by the backend'],
    ['views/public/publicknowledgebaseview/KnowledgeBaseSearchResults.vue', 'search snippet built by the database over stripped markup'],
    ['views/stationview/knowledge/knowledgebaseview/KbSearchResults.vue', 'search snippet built by the database over stripped markup'],
    ['views/stationview/knowledge/kbfileview/KbMarkdownView.vue', 'HTML handed down from the file view, which renders it through the shared renderer'],
    ['views/helpcenterstationview/HelpCenterSidebar.vue', 'snippet escaped in the file before its highlight markup is inserted'],
    ['views/loginview/ConsentGate.vue', 'legal document HTML rendered and sanitised by the backend'],
    ['views/loginview/LegalModal.vue', 'legal document HTML rendered and sanitised by the backend'],
    ['views/reconsentview/PolicyChangeSection.vue', 'legal document HTML rendered and sanitised by the backend'],
    ['components/legal/LegalDocument.vue', 'legal document HTML rendered and sanitised by the backend'],
    ['views/stationview/news/FederatedDetailView.vue', 'news HTML rendered and sanitised by the backend'],
    ['views/stationview/news/listview/NewsListItem.vue', 'news HTML rendered and sanitised by the backend'],
    ['views/stationview/news/newsshared/NewsBody.vue', 'news HTML rendered and sanitised by the backend'],
    ['views/helpcenter/stationview/news/DetailHelp.vue', 'constant help text written in the file itself'],
    ['views/helpcenter/stationview/news/FederatedDetailHelp.vue', 'constant help text written in the file itself'],
])

function sourcePath(file) {
    return rel(file).replace(/^src\//, '')
}

function lineOf(content, index) {
    return content.slice(0, index).split('\n').length
}

/**
 * Returns the bindings whose value the shared renderer produced. A call is attributed to the
 * nearest binding declared at the top of the script block, so the local variables inside a
 * computed do not stand in for the name the template actually shows.
 */
function renderedValues(content) {
    const values = new Set()
    const binding = /^(?:const|let)\s+([A-Za-z_$][\w$]*)\s*=/gm
    for (const call of RENDER_CALLS) {
        const usage = new RegExp(`${call}\\s*\\(`, 'g')
        let match
        while ((match = usage.exec(content)) !== null) {
            const before = content.slice(0, match.index)
            binding.lastIndex = 0
            let assignment
            let name = null
            while ((assignment = binding.exec(before)) !== null) {
                name = assignment[1]
            }
            if (name) values.add(name)
        }
    }
    return values
}

function importsRenderer(content) {
    return /from\s+['"][~@]\/util\/markdown['"]/.test(content)
}

let bindings = 0
const matched = new Set()

for (const file of walk(SRC, '.ts')) {
    if (sourcePath(file) === RENDERER) continue
    const content = readFileSync(file, 'utf-8')
    const marked = content.match(/from\s+['"]marked['"]/)
    if (marked) {
        error(file, lineOf(content, marked.index),
            `markdown is parsed outside ${RENDERER} - use renderMarkdown, which sanitises what marked produces`,
            CAT_MARKED)
    }
}

for (const file of walk(SRC, '.vue')) {
    const content = readFileSync(file, 'utf-8')
    const path = sourcePath(file)

    const marked = content.match(/from\s+['"]marked['"]/)
    if (marked) {
        error(file, lineOf(content, marked.index),
            `markdown is parsed outside ${RENDERER} - use renderMarkdown, which sanitises what marked produces`,
            CAT_MARKED)
    }

    const template = extractTemplate(content)
    if (!template) continue

    const rendered = renderedValues(content)
    const pattern = /v-html="([^"]*)"/g
    let match
    while ((match = pattern.exec(template)) !== null) {
        bindings++
        const expression = match[1].trim()
        const line = lineOf(content, content.indexOf(match[0]))
        const call = RENDER_CALLS.find(name => expression.startsWith(`${name}(`))
        if (call) {
            if (!importsRenderer(content)) {
                error(file, line, `${call} is called here but not imported from ~/util/markdown`, CAT_BINDING)
            }
            continue
        }

        const identifier = expression.replace(/^props\./, '').match(/^[A-Za-z_$][\w$]*/)?.[0] ?? ''
        if (rendered.has(identifier)) continue

        if (DECLARED.has(path)) {
            matched.add(path)
            continue
        }

        error(file, line,
            `v-html renders '${expression}', which the shared renderer did not produce - render it with `
            + 'renderMarkdown, or declare the site in lint-markdown-render.mjs with the reason it is safe',
            CAT_BINDING)
    }
}

for (const [path, reason] of DECLARED) {
    if (!matched.has(path)) {
        warn(path, 0, `declared v-html site no longer has a binding to cover (${reason})`, CAT_STALE)
    }
}

console.log(`Checked ${bindings} v-html bindings, ${DECLARED.size} of them declared`)
reporter.print()
process.exit(reporter.errors.length > 0 ? 1 : 0)

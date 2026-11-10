/**
 * Keeps one comment on a pull request that counts the lines it adds and removes per area and names the
 * kind of change from the changelog entries it adds, and sets the matching c: labels. Run by
 * actions/github-script from the pull-request-stats workflow.
 */

const MARKER = '<!-- pr-stats -->'

/** The API lists at most this many files of a pull request. */
const FILE_LIMIT = 3000

/** Files a tool writes: a generated folder, a .generated. file, the API spec and the schema tracking. */
const GENERATED_FILES = ['src/main/resources/api/openapi.json', 'src/main/resources/data_tracking.json']

function isGenerated(path) {
    return /(^|\/)generated\//.test(path) || /\.generated\./.test(path) || GENERATED_FILES.includes(path)
}

/**
 * Where a changed file belongs. The first area whose test matches wins, so generated files and
 * localisation are taken out before the broader frontend and backend areas.
 */
const AREAS = [
    {key: 'generated', label: 'Generated', matches: isGenerated},
    {key: 'feI18n', label: 'Frontend localisation', matches: f => f.startsWith('frontend/src/i18n/')},
    {key: 'beI18n', label: 'Backend localisation', matches: f => f.startsWith('src/main/resources/i18n/')},
    {key: 'feTests', label: 'Frontend tests', matches: f => f.startsWith('frontend/e2e/')
        || (f.startsWith('frontend/') && /\.(test|spec)\.[cm]?[jt]s$/.test(f))},
    {key: 'feLogic', label: 'Frontend logic', matches: f => f.startsWith('frontend/src/')},
    {key: 'feOther', label: 'Frontend other', matches: f => f.startsWith('frontend/')},
    {key: 'beTests', label: 'Backend tests', matches: f => f.startsWith('src/test/')},
    {key: 'beLogic', label: 'Backend logic', matches: f => f.startsWith('src/main/java/')},
    {key: 'beOther', label: 'Backend other', matches: f => f.startsWith('src/') || f.startsWith('buildSrc/')
        || f.startsWith('gradle/') || /^(build|settings)\.gradle\.kts$/.test(f)},
    {key: 'other', label: 'Other', matches: () => true},
]

/** The areas that are counted; generated files are shown apart, as nobody wrote them. */
const COUNTED = AREAS.map(area => area.key).filter(key => key !== 'generated')

/** The rows of the main table, each summing one or more areas. */
const SUMMARY = [
    {label: 'Frontend logic', areas: ['feLogic']},
    {label: 'Backend logic', areas: ['beLogic']},
    {label: 'Localisation', areas: ['feI18n', 'beI18n']},
    {label: 'Frontend', areas: ['feLogic', 'feTests', 'feI18n', 'feOther']},
    {label: 'Backend', areas: ['beLogic', 'beTests', 'beI18n', 'beOther']},
]

/** The changelog sections, in their order in the changelog, with the label each one sets. */
const KINDS = [
    {section: 'New Features', label: 'c:feature', color: '1d76db'},
    {section: 'Improvements', label: 'c:improvement', color: '0e8a16'},
    {section: 'Security', label: 'c:security', color: 'b60205'},
    {section: 'Changes', label: 'c:change', color: 'fbca04'},
    {section: 'Fixes', label: 'c:fix', color: 'd93f0b'},
]

const NONE = Object.freeze({files: 0, added: 0, removed: 0})

/**
 * Sums the changed files of a pull request per area.
 *
 * @param {{filename: string, additions: number, deletions: number}[]} files the changed files
 * @returns {Record<string, {files: number, added: number, removed: number}>} the sums by area key
 */
function sumByArea(files) {
    const sums = Object.fromEntries(AREAS.map(area => [area.key, {...NONE}]))
    for (const file of files) {
        const sum = sums[AREAS.find(area => area.matches(file.filename)).key]
        sum.files += 1
        sum.added += file.additions
        sum.removed += file.deletions
    }
    return sums
}

/** Adds up the sums of several areas. */
function total(sums, keys) {
    return keys.reduce((sum, key) => ({
        files: sum.files + sums[key].files,
        added: sum.added + sums[key].added,
        removed: sum.removed + sums[key].removed,
    }), {...NONE})
}

function format(n) {
    return n.toLocaleString('en-US')
}

/** One table row: files, added, removed and the net change, all in bold where asked. */
function row(label, sum, bold = false) {
    const net = sum.added - sum.removed
    const cells = [label, format(sum.files), `+${format(sum.added)}`, `-${format(sum.removed)}`,
        `${net >= 0 ? '+' : ''}${format(net)}`]
    return `| ${(bold ? cells.map(cell => `**${cell}**`) : cells).join(' | ')} |`
}

function table(rows) {
    return ['| Area | Files | Added | Removed | Net |', '|---|---:|---:|---:|---:|', ...rows]
}

/**
 * The entry titles of the newest release in a changelog, by section.
 *
 * @param {string} text the changelog
 * @returns {Record<string, Set<string>>} the titles, without their closing full stop
 */
function entriesOf(text) {
    const block = text.split(/^## /m)[1] ?? ''
    const entries = {}
    let section = null
    for (const line of block.split('\n')) {
        const heading = line.match(/^### (.+?)\s*$/)
        if (heading) section = heading[1]
        const title = line.match(/^- \*\*(.+?)\*\*/)
        if (section && title) (entries[section] ??= new Set()).add(title[1].replace(/\.$/, ''))
    }
    return entries
}

/**
 * The changelog entries the head adds to the newest release over the base, by kind of change.
 *
 * @returns {{section: string, label: string, color: string, titles: string[]}[]} every kind, with the
 *     titles it gained, empty where none
 */
function addedEntries(baseText, headText) {
    const before = entriesOf(baseText)
    const after = entriesOf(headText)
    return KINDS.map(kind => ({
        ...kind,
        titles: [...(after[kind.section] ?? [])].filter(title => !before[kind.section]?.has(title)),
    }))
}

/** The changelog as it stands at a commit, or nothing where there is none. */
async function changelogAt(github, owner, repo, ref) {
    try {
        const {data} = await github.rest.repos.getContent({owner, repo, path: 'CHANGELOG.md', ref})
        return Buffer.from(data.content, 'base64').toString('utf8')
    } catch (e) {
        if (e.status === 404) return ''
        throw e
    }
}

/** Sets the c: label of every kind the pull request adds entries to and takes off the others. */
async function syncLabels(github, owner, repo, number, current, kinds) {
    for (const kind of kinds) {
        const wanted = kind.titles.length > 0
        const present = current.includes(kind.label)
        if (wanted && !present) {
            await ensureLabel(github, owner, repo, kind)
            await github.rest.issues.addLabels({owner, repo, issue_number: number, labels: [kind.label]})
        }
        if (!wanted && present) {
            await github.rest.issues.removeLabel({owner, repo, issue_number: number, name: kind.label})
        }
    }
}

/** Creates a label in its colour the first time it is needed; one that exists already stays as it is. */
async function ensureLabel(github, owner, repo, kind) {
    try {
        await github.rest.issues.createLabel({owner, repo, name: kind.label, color: kind.color})
    } catch (e) {
        if (e.status !== 422) throw e
    }
}

/** The comment: the kind of change, the main table, then the areas and the changelog entries folded away. */
function render(sums, kinds, fileCount, headSha) {
    const found = kinds.filter(kind => kind.titles.length > 0)
    const summary = found.length > 0
        ? `**Kind:** ${found.map(kind => `${kind.section} (${kind.titles.length})`).join(', ')}`
        : '**Kind:** no changelog entry'
    const changelog = found.length > 0
        ? ['', '<details><summary>Changelog entries</summary>', '',
            ...found.flatMap(kind => [`**${kind.section}**`, ...kind.titles.map(title => `- ${title}`), '']),
            '</details>']
        : []
    const capped = fileCount >= FILE_LIMIT
        ? ['', `> The API lists at most ${FILE_LIMIT} files, so this pull request is counted only in part.`]
        : []
    return [
        MARKER,
        '### Pull request stats',
        '',
        summary,
        '',
        ...table([
            ...SUMMARY.map(line => row(line.label, total(sums, line.areas))),
            row('Total', total(sums, COUNTED), true),
            row('Generated (not counted)', sums.generated),
        ]),
        '',
        '<details><summary>By area</summary>',
        '',
        ...table(AREAS.filter(area => sums[area.key].files > 0).map(area => row(area.label, sums[area.key]))),
        '',
        '</details>',
        ...changelog,
        ...capped,
        '',
        `<sub>Updated for ${headSha.slice(0, 9)}.</sub>`,
    ].join('\n')
}

/** Edits the stats comment where there is one and posts it otherwise. */
async function upsertComment(github, owner, repo, number, body) {
    const comments = await github.paginate(github.rest.issues.listComments,
        {owner, repo, issue_number: number, per_page: 100})
    const mine = comments.find(comment => comment.user.type === 'Bot' && comment.body.startsWith(MARKER))
    if (mine) {
        await github.rest.issues.updateComment({owner, repo, comment_id: mine.id, body})
    } else {
        await github.rest.issues.createComment({owner, repo, issue_number: number, body})
    }
}

/**
 * Counts, labels and comments for the pull request the workflow runs for.
 *
 * @param {{github: object, context: object}} script what actions/github-script hands over
 */
async function run({github, context}) {
    const {owner, repo} = context.repo
    const pull = context.payload.pull_request
    const files = await github.paginate(github.rest.pulls.listFiles,
        {owner, repo, pull_number: pull.number, per_page: 100})
    const kinds = addedEntries(
        await changelogAt(github, owner, repo, pull.base.sha),
        await changelogAt(github, owner, repo, pull.head.sha))
    await syncLabels(github, owner, repo, pull.number, pull.labels.map(label => label.name), kinds)
    await upsertComment(github, owner, repo, pull.number, render(sumByArea(files), kinds, files.length, pull.head.sha))
}

module.exports = run
module.exports.sumByArea = sumByArea
module.exports.addedEntries = addedEntries
module.exports.render = render

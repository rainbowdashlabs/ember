/**
 * Every linter the frontend is held to, in the order they run.
 *
 * <p>This is the one list. `npm run lint`, `npm run lint:audit`, the build and CI all read it, so a
 * linter green on one of them is green on all of them. Each entry is a name, which is what
 * `./toolchain.sh fe-lint <name>` takes, and the command that runs it from the frontend directory.
 *
 * <p>ESLint carries the rules about the code itself, the project's own among them. jscpd measures
 * duplication. The scripts that remain check what is not code in a file ESLint reads: the help
 * centre against the routes, the search index against the pages, the generated API types against the
 * backend's API description, the storage catalogue, the two
 * changelogs, the registered icons, and the em dash and comment rules, which reach the Java sources
 * as well.
 */
export const LINTERS = [
    {name: 'eslint', command: ['node', 'node_modules/eslint/bin/eslint.js', '--max-warnings=0']},
    {name: 'duplication', command: ['node', 'node_modules/jscpd/run-jscpd.js', '--config', '.jscpd.json']},
    {name: 'icons', command: ['node', 'scripts/lint-icons.mjs']},
    {name: 'helpcenter', command: ['node', 'scripts/lint-helpcenter.mjs']},
    {name: 'help-index', command: ['node', 'scripts/lint-help-index.mjs']},
    {name: 'api-types', command: ['node', 'scripts/lint-api-types.mjs']},
    {name: 'browser-storage', command: ['node', 'scripts/lint-browser-storage.mjs']},
    {name: 'em-dash', command: ['node', 'scripts/lint-em-dash.mjs']},
    {name: 'comments', command: ['node', 'scripts/lint-comments.mjs']},
    {name: 'changelog-languages', command: ['node', 'scripts/lint-changelog-languages.mjs']},
]

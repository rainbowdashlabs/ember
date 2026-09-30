/**
 * Every linter the frontend is held to, in the order they run.
 *
 * <p>This is the one list. `npm run lint`, `npm run lint:audit`, the build and CI all read it, so a
 * linter green on one of them is green on all of them. Each entry is a name, which is what
 * `./toolchain.sh fe-lint <name>` takes, and the command that runs it from the frontend directory.
 */
export const LINTERS = [
    {name: 'eslint', command: ['node', 'node_modules/eslint/bin/eslint.js', '--max-warnings=0']},
    {name: 'icons', command: ['node', 'scripts/lint-icons.mjs']},
    {name: 'conventions', command: ['node', 'scripts/lint-conventions.mjs']},
    {name: 'helpcenter', command: ['node', 'scripts/lint-helpcenter.mjs']},
    {name: 'help-index', command: ['node', 'scripts/lint-help-index.mjs']},
    {name: 'helpcenter-i18n', command: ['node', 'scripts/lint-helpcenter-i18n.mjs']},
    {name: 'locales', command: ['node', 'scripts/lint-locales.mjs']},
    {name: 'imports', command: ['node', 'scripts/lint-imports.mjs']},
    {name: 'style', command: ['node', 'scripts/lint-style.mjs']},
    {name: 'i18n-keys', command: ['node', 'scripts/lint-i18n-keys.mjs']},
    {name: 'component-size', command: ['node', 'scripts/lint-component-size.mjs']},
    {name: 'duplication', command: ['node', 'scripts/lint-duplication.mjs']},
    {name: 'page-titles', command: ['node', 'scripts/lint-page-titles.mjs']},
    {name: 'browser-storage', command: ['node', 'scripts/lint-browser-storage.mjs']},
    {name: 'em-dash', command: ['node', 'scripts/lint-em-dash.mjs']},
    {name: 'comments', command: ['node', 'scripts/lint-comments.mjs']},
    {name: 'markdown-render', command: ['node', 'scripts/lint-markdown-render.mjs']},
    {name: 'stacked-text', command: ['node', 'scripts/lint-stacked-text.mjs']},
    {name: 'button-rows', command: ['node', 'scripts/lint-button-rows.mjs']},
    {name: 'page-links', command: ['node', 'scripts/lint-page-links.mjs']},
    {name: 'generic-errors', command: ['node', 'scripts/lint-generic-errors.mjs']},
    {name: 'social-meta', command: ['node', 'scripts/lint-social-meta.mjs']},
    {name: 'context-titles', command: ['node', 'scripts/lint-context-titles.mjs']},
    {name: 'story-actors', command: ['node', 'scripts/lint-story-actors.mjs', '--error']},
    {name: 'changelog-languages', command: ['node', 'scripts/lint-changelog-languages.mjs']},
]

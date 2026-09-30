import vueI18n from '@intlify/eslint-plugin-vue-i18n'
import {createTypeScriptImportResolver} from 'eslint-import-resolver-typescript'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import withNuxt from './.nuxt/eslint.config.mjs'
import {localeSnapshots} from './eslint/locales.mjs'

/**
 * Which layer may not reach which, as `import/no-restricted-paths` zones.
 *
 * <p>The bottom layers do not import from the ones above them, so that lifting a piece out never
 * means dragging half the application along: the API clients and the translations stand on nothing
 * of the interface, and components and composables stand on no view, layout or page.
 */
const LAYERS = [
    {layer: 'api', above: ['views', 'components', 'composables', 'layouts', 'pages']},
    {layer: 'components', above: ['views', 'layouts', 'pages']},
    {layer: 'composables', above: ['views', 'layouts', 'pages']},
    {layer: 'i18n', above: ['views', 'components', 'composables', 'layouts', 'pages']},
]

/**
 * The `v-html` bindings that do not show what the shared markdown renderer produced, each with the
 * reason it is safe.
 *
 * <p>`marked` passes raw HTML through by design, so markdown parsed anywhere but `util/markdown.ts`
 * is stored cross-site scripting waiting for a reader. A binding is therefore either a call to that
 * renderer, a value a file computes with it, or one of these, checked by a person.
 */
const SANITISED_HTML = [
    {file: 'src/views/public/publickbfileview/KbFileRenderer.vue', reason: 'knowledge base HTML rendered and sanitised by the backend'},
    {file: 'src/views/stationview/knowledge/knowledgebaseview/KbSearchResults.vue', reason: 'search snippet built by the database over stripped markup'},
    {file: 'src/views/stationview/knowledge/kbfileview/KbMarkdownView.vue', reason: 'HTML handed down from the file view, which renders it through the shared renderer'},
    {file: 'src/views/helpcenterstationview/HelpCenterSidebar.vue', reason: 'snippet escaped in the file before its highlight markup is inserted'},
    {file: 'src/views/loginview/ConsentGate.vue', reason: 'legal document HTML rendered and sanitised by the backend'},
    {file: 'src/views/loginview/LegalModal.vue', reason: 'legal document HTML rendered and sanitised by the backend'},
    {file: 'src/views/reconsentview/PolicyChangeSection.vue', reason: 'legal document HTML rendered and sanitised by the backend'},
    {file: 'src/components/legal/LegalDocument.vue', reason: 'legal document HTML rendered and sanitised by the backend'},
    {file: 'src/components/display/ProseExcerpt.vue', reason: 'HTML handed down by whoever shows the excerpt, rendered through the shared renderer or sanitised by the backend'},
    {file: 'src/views/stationview/news/FederatedDetailView.vue', reason: 'news HTML rendered and sanitised by the backend'},
    {file: 'src/views/stationview/news/newsshared/NewsBody.vue', reason: 'news HTML rendered and sanitised by the backend'},
    {file: 'src/views/helpcenter/stationview/news/DetailHelp.vue', reason: 'constant help text written in the file itself'},
    {file: 'src/views/helpcenter/stationview/news/FederatedDetailHelp.vue', reason: 'constant help text written in the file itself'},
    {file: 'src/components/content/ContentCell.vue', reason: 'computed in the file with renderPageMarkdown'},
    {file: 'src/components/content/blockeditor/CellMarkdownInline.vue', reason: 'computed in the file with renderMarkdown'},
    {file: 'src/components/content/blockeditor/cells/AccordionCell.vue', reason: 'computed in the file with renderPageMarkdown'},
    {file: 'src/components/content/blockeditor/cells/CalloutCell.vue', reason: 'computed in the file with renderMarkdown'},
    {file: 'src/components/input/text/MarkdownFieldInput.vue', reason: 'computed in the file with renderMarkdown'},
    {file: 'src/views/adminview/adminlegalview/FileListPanel.vue', reason: 'computed in the file with renderMarkdown'},
]

/**
 * Vue's rules as the components here are written.
 *
 * <p>Blocks go script, template, style. An element with nothing inside closes itself, a void one
 * included. Where the first attribute of an element written over several lines stands is
 * formatting, like the rest of Vue's layout rules that Nuxt's config switches off when its
 * stylistic preset is off; this one it leaves on, and its fix cannot indent what it moves.
 */
const VUE_RULES = {
    'vue/block-order': ['error', {order: ['script', 'template', 'style']}],
    'vue/first-attribute-linebreak': 'off',
    'vue/html-self-closing': ['error', {html: {void: 'always', normal: 'always', component: 'always'}, svg: 'always', math: 'always'}],
    'vue/no-v-html': ['error', {ignorePattern: '^render(Page)?Markdown\\('}],
}

const IDENTITY_FIELDS = '/^(email|lastName|firstName|username)$/'

/** A state type written inline, which wants a name of its own. */
const INLINE_STATE_TYPE = {
    selector: "CallExpression[callee.name='ref']:matches([typeArguments.params.0.type='TSTypeLiteral'], [typeArguments.params.0.types.0.type='TSTypeLiteral'])",
    message: 'Inline object type in ref<>: give the type a name.',
}

/**
 * The generic "that did not work", which says neither what failed nor what to do about it.
 * `describeFailure` from `util/failure.ts` does, and `FailureAlert` renders what it says.
 */
const GENERIC_FAILURE = {
    selector: "CallExpression:matches([callee.name=/^\\$?t$/], [callee.property.name='t'])[arguments.length=1][arguments.0.value='common.error']",
    message: 'Answers a failure with "something went wrong". Describe it: describeFailure(e, t) from util/failure.ts, rendered with FailureAlert.',
}

/**
 * A date formatted for the locale in place. `toLocaleString` also formats numbers, which pass the
 * locale alone, while a date always passes options after it.
 */
const INLINE_DATE_FORMAT = {
    selector: "CallExpression:matches([callee.property.name=/^toLocale(Date|Time)String$/], [callee.property.name='toLocaleString'][arguments.length>1])",
    message: 'Inline toLocale date formatting: use the helpers in util/format.ts.',
}

/**
 * The same syntax restriction for the script and for the template of a component.
 *
 * @param restrictions the selectors with their messages
 * @returns the rules entry for both
 */
function restrictSyntax(...restrictions) {
    return {
        'no-restricted-syntax': ['error', ...restrictions],
        'vue/no-restricted-syntax': ['error', ...restrictions],
    }
}

export default withNuxt(
    {
        name: 'ember/ignores',
        ignores: ['e2e/report/**', 'e2e/results/**', 'e2e/.auth/**', 'public/**', 'coverage/**'],
    },
    {
        name: 'ember/settings',
        settings: {
            'import-x/resolver-next': [createTypeScriptImportResolver({project: './tsconfig.json'})],
            'vue-i18n': {
                localeDir: await localeSnapshots(),
                messageSyntaxVersion: '^11.0.0',
            },
        },
    },
    {
        name: 'ember/typescript',
        rules: {
            'no-restricted-properties': ['error',
                {object: 'console', property: 'log', message: 'Leftover console.log: remove it.'},
                {object: 'console', property: 'debug', message: 'Leftover console.debug: remove it.'},
            ],
        },
    },
    ...vueA11y.configs['flat/recommended'],
    {
        name: 'ember/vue',
        files: ['**/*.vue'],
        rules: VUE_RULES,
    },
    {
        name: 'ember/sanitised-html',
        files: SANITISED_HTML.map(entry => entry.file),
        rules: {
            'vue/no-v-html': 'off',
        },
    },
    {
        name: 'ember/vue-i18n',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/i18n/**', 'src/**/*.test.ts'],
        plugins: {'@intlify/vue-i18n': vueI18n},
        rules: {
            '@intlify/vue-i18n/no-missing-keys': 'error',
        },
    },
    {
        name: 'ember/help-center-text',
        files: ['src/views/helpcenter/**/*.vue'],
        plugins: {'@intlify/vue-i18n': vueI18n},
        rules: {
            '@intlify/vue-i18n/no-raw-text': ['error', {ignorePattern: '^[-#:()&.,/·+|→←↑↓…\\s\\d]+$'}],
        },
    },
    {
        name: 'ember/markdown',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/util/markdown.ts'],
        rules: {
            'no-restricted-imports': ['error', {
                paths: [{name: 'marked', message: 'Parse markdown with renderMarkdown from ~/util/markdown, which sanitises what marked produces.'}],
            }],
        },
    },
    ...LAYERS.map(({layer, above}) => ({
        name: `ember/layers/${layer}`,
        files: [`src/${layer}/**/*.{ts,vue}`],
        rules: {
            'import/no-restricted-paths': ['error', {
                zones: above.map(from => ({
                    target: `./src/${layer}`,
                    from: `./src/${from}`,
                    message: `${layer}/ may not import from ${from}/.`,
                })),
            }],
        },
    })),
    {
        name: 'ember/source-size',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/**/*.test.ts', 'src/i18n/**'],
        rules: {
            'max-lines-per-function': ['error', {max: 79, IIFEs: true}],
        },
    },
    {
        name: 'ember/state-types',
        files: ['src/**/*.{ts,vue}'],
        rules: restrictSyntax(INLINE_STATE_TYPE),
    },
    {
        name: 'ember/failures',
        files: ['src/views/**/*.{ts,vue}', 'src/components/**/*.{ts,vue}', 'src/composables/**/*.{ts,vue}'],
        ignores: ['src/components/feedback/FailureAlert.vue'],
        rules: restrictSyntax(INLINE_STATE_TYPE, GENERIC_FAILURE),
    },
    {
        name: 'ember/views',
        files: ['src/views/**/*.vue'],
        rules: {
            ...restrictSyntax(INLINE_STATE_TYPE, GENERIC_FAILURE, INLINE_DATE_FORMAT),
            'max-lines': ['error', {max: 500}],
        },
    },
    {
        name: 'ember/stories',
        files: ['e2e/**/*.e2e.ts'],
        rules: {
            'no-restricted-syntax': ['error',
                {
                    selector: 'CallExpression:matches([callee.name=/^(accountWith|accountWithout|stationPeers|instanceAdmin)$/], [callee.property.name=/^(accountWith|accountWithout|stationPeers|instanceAdmin)$/])',
                    message: 'A story works out who to act as. The cast settles that at global setup: read it from fixtures/cast.ts instead.',
                },
                {
                    selector: `BinaryExpression[operator=/^[!=]==$/]:matches([left.name=${IDENTITY_FIELDS}], [left.property.name=${IDENTITY_FIELDS}], [right.property.name=${IDENTITY_FIELDS}])`,
                    message: 'An identity is compared by address or name, which the stories rewrite. Compare the id.',
                },
            ],
        },
    },
)

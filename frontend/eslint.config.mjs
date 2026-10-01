import {readdirSync} from 'node:fs'
import vueI18n from '@intlify/eslint-plugin-vue-i18n'
import {createTypeScriptImportResolver} from 'eslint-import-resolver-typescript'
import vueA11y from 'eslint-plugin-vuejs-accessibility'
import withNuxt from './.nuxt/eslint.config.mjs'
import * as localeParser from './eslint/i18n/locale-parser.mjs'
import ember from './eslint/index.mjs'
import {localeModules, localeSnapshots, refusalAreaFiles} from './eslint/locales.mjs'
import {
    GENERIC_FAILURE,
    INLINE_DATE_FORMAT,
    INLINE_STATE_TYPE,
    MUTABLE_IDENTITY,
    STORY_CASTING,
    restrictSyntax,
} from './eslint/restrictions.mjs'

/**
 * The messages as the application holds them, for the rules that look keys up. The locale modules
 * themselves are added to this for the locale rules only: the plugin cannot load their messages,
 * which the rules on components would trip over.
 */
const SNAPSHOTS = await localeSnapshots()

/** The refusal areas, each a file of German texts merged in under `refusal`. */
const REFUSAL_AREAS = refusalAreaFiles()

/** The locale modules, which only the locale rules read. */
const LOCALE_FILES = ['src/i18n/de-DE.ts', 'src/i18n/de-DE.*.ts', 'src/i18n/en.ts', ...REFUSAL_AREAS]

/** The German messages: the main file, and the blocks that live in files of their own under a prefix. */
const GERMAN = [
    {file: 'src/i18n/de-DE.ts'},
    ...REFUSAL_AREAS.map(file => ({file, prefix: 'refusal'})),
    {file: 'src/i18n/de-DE.helpcenter.ts', prefix: 'helpCenter'},
]

const JAVA = '../src/main/java/dev/chojo/ember/'

/** The backend sources that send a message key to the frontend as data. */
const BACKEND_KEY_FILES = [
    `${JAVA}feature/notifications/entity/NotificationType.java`,
    `${JAVA}feature/account/route/AuthRoutes.java`,
]

/** The refusal enums, one per area, beside the `Refusal.java` that registers the areas. */
const REFUSAL_FILES = readdirSync(`${import.meta.dirname}/${JAVA}api/refusal`)
    .filter(name => name.endsWith('Refusal.java') && name !== 'Refusal.java')
    .sort()
    .map(name => `${JAVA}api/refusal/${name}`)

/** Locale sections holding one entry per constant of a backend enum. */
const ENUM_SECTIONS = [
    {enumFiles: REFUSAL_FILES, prefix: 'refusal', reader: 'refusal-codes'},
    {enumFiles: [`${JAVA}api/auth/StationPermission.java`, `${JAVA}api/auth/ClusterPermission.java`], prefix: 'permissions', leaves: ['label', 'desc']},
    {enumFiles: [`${JAVA}feature/notifications/entity/NotificationType.java`], prefix: 'notification.typeLabel'},
    {enumFiles: [`${JAVA}feature/twofactor/entity/TwoFactorEvent.java`], prefix: 'twoFactor.admin.audit.events'},
    {enumFiles: [`${JAVA}feature/twofactor/entity/TwoFactorKind.java`], prefix: 'twoFactor.admin.audit.factors'},
    {enumFiles: [`${JAVA}feature/mail/entity/MailDeliveryStatus.java`], prefix: 'mailDashboard.delivery'},
]

/**
 * Which layer may not reach which, as `import/no-restricted-paths` zones.
 *
 * <p>The bottom layers do not import from the ones above them, so that lifting a piece out never
 * means dragging half the application along: the API clients and the translations stand on nothing
 * of the interface, and components and composables stand on no view, layout or page.
 */
const LAYERS = [
    {layer: 'api', above: ['views', 'components', 'composables', 'layouts', 'pages']},
    {layer: 'components', above: ['views', 'layouts', 'pages'], stateThroughComposables: true},
    {layer: 'composables', above: ['views', 'layouts', 'pages']},
    {layer: 'i18n', above: ['views', 'components', 'composables', 'layouts', 'pages']},
    {layer: 'views', above: [], stateThroughComposables: true},
    {layer: 'layouts', above: [], stateThroughComposables: true},
    {layer: 'pages', above: [], stateThroughComposables: true},
]

/**
 * The modules holding state that more than one composable builds on.
 *
 * <p>Only composables, other `util` modules, the API clients, plugins and middleware reach them. A view,
 * component, layout or page goes through the composable, which hands out read-only views and the
 * setters that belong to them. A test may still arrange the state it starts from.
 *
 * <p>The exceptions hand out no writable state: `browserState` is the helper the state is built with,
 * `formState` works out whether a form takes answers, and the problem report and the sidebar match are
 * browser machinery behind a function API, like the toasts, which components call directly.
 */
const SHARED_STATE = {
    from: './src/util/*State.ts',
    except: [`${import.meta.dirname}/src/util/{browserState,formState,problemReportState,sidebarGroupState}.ts`],
}

/**
 * The `import/no-restricted-paths` zones of one layer.
 *
 * @param layer the layer, as listed in {@link LAYERS}
 * @returns the zones its files are held to
 */
function layerZones({layer, above, stateThroughComposables}) {
    const zones = above.map(from => ({
        target: `./src/${layer}`,
        from: `./src/${from}`,
        message: `${layer}/ may not import from ${from}/.`,
    }))
    if (!stateThroughComposables) return zones
    return [...zones, {
        target: `./src/${layer}/**/!(*.test).{ts,vue}`,
        ...SHARED_STATE,
        message: `${layer}/ reaches shared state through its composable, not util/*State.ts.`,
    }]
}

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
 * `ProseContent` is a single element carrying the prose classes and is built to take `v-html`
 * through attribute fallthrough, so it is the one component the HTML may be bound on.
 */
const VUE_RULES = {
    'vue/block-order': ['error', {order: ['script', 'template', 'style']}],
    'vue/first-attribute-linebreak': 'off',
    'vue/html-self-closing': ['error', {html: {void: 'always', normal: 'always', component: 'always'}, svg: 'always', math: 'always'}],
    'vue/no-v-text-v-html-on-component': ['error', {allow: ['ProseContent']}],    'vue/no-v-html': ['error', {ignorePattern: '^render(Page)?Markdown\\('}],
}

/**
 * Text on a help page that reads the same in every language, so there is nothing to translate: a
 * run without letters, a quantity with its unit, an upper-case code, a multiplier, a version, a data
 * path, an address path, a URL and an e-mail address. Everything else on a help page goes through
 * `t()`, and text inside `<code>` is left alone as well.
 */
const LANGUAGE_NEUTRAL_TEXT = [
    '[\\p{N}\\p{P}\\p{S}\\s]+',
    '[<>≤≥]?\\s?\\d[\\d.,]*\\s?(?:ms|s|min|h|%|[KMGT]i?B)?(?:\\s?[–-]\\s?\\d[\\d.,]*\\s?(?:ms|s|min|h|%|[KMGT]i?B)?)?',
    '[\\[(]?[A-Z0-9]+(?:[-/][A-Z0-9]+)*(?:\\s?→\\s?[A-Z0-9]+)?[\\])]?(?:\\s[-–·(])?',
    '[x×]',
    'v\\d+(?:\\.\\d+)*',
    '[a-z]\\w*\\[\\d+\\]:?',
    '/[\\w/{}:.?=&-]*',
    'https?://\\S+',
    '[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+(?:,\\s[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)*',
]

/**
 * The components whose root is a native form control, or a switch button, which a label names
 * like the element itself.
 *
 * <p>A label is associated with its control by wrapping it or by pointing at its id, either of the
 * two, which is what the accessibility tree reads. The rule's default asks for both at once.
 */
const CONTROL_COMPONENTS = [
    'BaseInput', 'CheckboxInput', 'RadioInput', 'SelectInput', 'TextAreaInput', 'TextInput', 'NumberInput',
    'DecimalInput', 'PasswordInput', 'DateInput', 'DateTimeInput', 'TimeInput', 'TimeShortInput', 'ToggleInput',
    'CompactToggle',
]

/**
 * The players of video and audio people upload, which carry no captions: nothing in the product lets
 * anybody attach a caption track to an upload yet, and an empty track would only claim one.
 */
const UNCAPTIONED_MEDIA = [
    'src/components/content/ContentCell.vue',
    'src/components/content/blockeditor/CellVideoEditor.vue',
    'src/components/content/blockeditor/cells/AudioEmbedCell.vue',
    'src/components/documents/FileView.vue',
    'src/views/stationview/media/mediaview/MediaFilePreviewModal.vue',
]

export default withNuxt(
    {
        name: 'ember/ignores',
        ignores: ['e2e/report/**', 'e2e/results/**', 'e2e/.auth/**', 'public/**', 'coverage/**', 'eslint/**/fixtures/**', 'src/api/generated/**'],
    },
    {
        name: 'ember/settings',
        settings: {
            'import-x/resolver-next': [createTypeScriptImportResolver({project: './tsconfig.json'})],
            'vue-i18n': {
                localeDir: SNAPSHOTS,
                messageSyntaxVersion: '^11.0.0',
            },
        },
    },
    {
        name: 'ember/plugin',
        plugins: {ember},
    },
    {
        name: 'ember/console',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/plugins/**', 'src/**/debug/**', 'src/**/*init.client.ts'],
        rules: {
            'no-restricted-properties': ['error',
                {object: 'console', property: 'log', message: 'Leftover console.log: remove it.'},
                {object: 'console', property: 'debug', message: 'Leftover console.debug: remove it.'},
            ],
        },
    },
    ...vueA11y.configs['flat/recommended'],
    {
        name: 'ember/accessibility',
        files: ['**/*.vue'],
        rules: {
            'vuejs-accessibility/label-has-for': ['error', {
                required: {some: ['nesting', 'id']},
                allowChildren: true,
                controlComponents: CONTROL_COMPONENTS,
            }],
        },
    },
    {
        name: 'ember/uncaptioned-media',
        files: UNCAPTIONED_MEDIA,
        rules: {
            'vuejs-accessibility/media-has-caption': 'off',
        },
    },
    {
        name: 'ember/vue',
        files: ['**/*.vue'],
        rules: VUE_RULES,
    },
    {
        name: 'ember/templates',
        files: ['src/**/*.vue'],
        rules: {
            'ember/button-row': 'error',
            'ember/no-dead-button-size': 'error',
            'ember/no-stacked-inline-text': 'error',
            'ember/rows-open-pages-as-links': 'error',
            'ember/styled-elements': 'error',
            'ember/view-content-title': 'error',
        },
    },
    {
        name: 'ember/pages',
        files: ['src/pages/**/*.vue'],
        rules: {
            'ember/context-title': 'error',
            'ember/route-view-content': 'error',
            'ember/social-meta': 'error',
        },
    },
    {
        name: 'ember/view-templates',
        files: ['src/**/*.vue'],
        ignores: ['src/components/**'],
        rules: {
            'ember/repeated-class-pattern': 'error',
            'ember/section-density': 'error',
            'ember/template-block-size': 'error',
        },
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
        name: 'ember/locales',
        files: LOCALE_FILES,
        languageOptions: {parser: localeParser},
        plugins: {'@intlify/vue-i18n': vueI18n},
        settings: {
            'vue-i18n': {
                localeDir: [SNAPSHOTS, localeModules()],
                messageSyntaxVersion: '^11.0.0',
            },
        },
        rules: {
            '@intlify/vue-i18n/valid-message-syntax': 'error',
            'ember/i18n-no-plural-messages': 'error',
            'ember/i18n-unused-keys': ['error', {
                sources: 'src',
                german: GERMAN,
                translations: ['src/i18n/en.ts'],
                backendKeyFiles: BACKEND_KEY_FILES,
            }],
            'ember/i18n-backend-keys': ['error', {german: GERMAN, sections: ENUM_SECTIONS, keyFiles: BACKEND_KEY_FILES}],
        },
    },
    {
        name: 'ember/help-center-text',
        files: ['src/views/helpcenter/**/*.vue'],
        plugins: {'@intlify/vue-i18n': vueI18n},
        rules: {
            '@intlify/vue-i18n/no-raw-text': ['error', {
                ignorePattern: `^(?:${LANGUAGE_NEUTRAL_TEXT.join('|')})$`,
                ignoreNodes: ['code'],
            }],
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
    ...LAYERS.map(layer => ({
        name: `ember/layers/${layer.layer}`,
        files: [`src/${layer.layer}/**/*.{ts,vue}`],
        rules: {
            'import/no-restricted-paths': ['error', {zones: layerZones(layer)}],
        },
    })),
    {
        name: 'ember/source-size',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/**/*.test.ts', 'src/i18n/**'],
        rules: {
            'ember/max-function-lines': ['error', {max: 79}],
        },
    },
    {
        name: 'ember/module-state',
        files: ['src/**/*.{ts,vue}'],
        ignores: ['src/**/*.test.ts', 'src/test/**'],
        rules: {
            'ember/no-module-state': 'error',
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
            'no-restricted-syntax': ['error', STORY_CASTING, MUTABLE_IDENTITY],
        },
    },
    {
        name: 'ember/fixtures',
        files: ['e2e/**/*.ts'],
        rules: {
            'no-empty-pattern': ['error', {allowObjectPatternsAsParameters: true}],
        },
    },
).onResolved(configs => configs.map(keepOffLocales))

/**
 * Leaves the locale modules to the locale rules.
 *
 * <p>The locale parser hands ESLint a JSON AST, which no rule written for a script can read, so
 * every entry that switches rules on, or runs a processor, ignores those files. The entries that
 * only register plugins or carry settings stay global, since the locale rules need both.
 *
 * @param config one resolved config entry
 * @returns the entry, ignoring the locale modules where it would apply rules to them
 */
function keepOffLocales(config) {
    if (config.name === 'ember/locales') return config
    if (!config.rules && !config.processor) return config
    return {...config, ignores: [...(config.ignores ?? []), ...LOCALE_FILES]}
}

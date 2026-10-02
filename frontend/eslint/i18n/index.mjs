import backendKeys from './backend-keys.mjs'
import noPluralMessages from './no-plural-messages.mjs'
import unusedKeys from './unused-keys.mjs'

/**
 * The rules for the locale files, which run on the JSON AST the locale parser gives them.
 */
export const i18nRules = {
    'i18n-backend-keys': backendKeys,
    'i18n-no-plural-messages': noPluralMessages,
    'i18n-unused-keys': unusedKeys,
}

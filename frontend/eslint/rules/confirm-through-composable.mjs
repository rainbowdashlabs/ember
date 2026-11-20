import {directive, staticAttribute, walkElements} from './vue-template.mjs'

/** The composables that own a confirmation, and so its `show`. */
const CONFIRM_COMPOSABLES = new Set(['useConfirmAction', 'useConfirmDelete'])

/**
 * Composables that wrap one of the two and hand its `show` on under a name of their own. Whatever
 * they return may drive a confirmation.
 */
export const CONFIRM_WRAPPERS = new Set([
    'useAnswerLossConsent',
    'useConversationConfirm',
    'useEntryDeletion',
    'useFieldsConfig',
    'useGroupsConfig',
    'useListInvites',
    'useMediaBulkActions',
    'useMediaEditing',
    'useMediaFolderForm',
    'useMediaTagForm',
    'useProblemList',
    'useQueueActions',
    'useSetMoves',
])

/** The marker a dialog carries that asks every time on purpose: a safety stop or a dialog taking input. */
const ALWAYS_ASKS = 'data-confirm-always'

/** Components whose name says they are a confirmation that a parent opens through a model. */
const CONFIRM_COMPONENT = /Confirm\w*(Modal|Dialog)s?$|Delete\w*Modals?$|Remove\w*Modal$|Dialogs$/

/** Buttons that carry out something destructive, which make a plain `Modal` a confirmation. */
const DESTRUCTIVE_BUTTON = /^(Error|Delete)Button$/

/**
 * What the names declared at the top of a component's script are bound to, as far as this rule cares.
 *
 * <p>A name bound to the result of a confirmation composable is its `owner`, and destructuring `show`
 * from it, directly or later, gives a `confirm`. Everything a wrapper returns, and everything taken
 * out of that, counts as the wrapper. Props typed after a wrapper, and a `computed` reading from a
 * wrapper's name, pass a wrapper's question on and count as it too.
 *
 * @param program the script program
 * @param sourceCode the source, to read type annotations and computed bodies
 * @returns a map from name to `confirm`, `owner`, `wrapper`, `model` or `ref`
 */
function declaredNames(program, sourceCode) {
    const names = new Map()
    for (const statement of program.body) {
        if (statement.type !== 'VariableDeclaration') continue
        for (const declarator of statement.declarations) {
            const kind = initKind(declarator.init, names, sourceCode)
            if (!kind) continue
            if (declarator.id.type === 'Identifier') names.set(declarator.id.name, kind === 'confirm' ? 'owner' : kind)
            else if (declarator.id.type === 'ObjectPattern') bindPattern(declarator.id, kind, names)
        }
    }
    return names
}

/**
 * Records the names a destructuring takes out of a confirmation or a wrapper.
 *
 * @param pattern the object pattern
 * @param kind what it destructures
 * @param names the map to record into
 */
function bindPattern(pattern, kind, names) {
    for (const property of pattern.properties) {
        if (property.type !== 'Property' || property.value.type !== 'Identifier') continue
        const key = property.key.type === 'Identifier' ? property.key.name : null
        if ((kind === 'confirm' || kind === 'owner') && key === 'show') names.set(property.value.name, 'confirm')
        else if (kind === 'wrapper') names.set(property.value.name, 'wrapper')
    }
}

/**
 * Which kind of value a declaration is initialised with.
 *
 * @param init the initialiser
 * @param names what the names declared before it are bound to
 * @param sourceCode the source, to read type annotations and computed bodies
 * @returns `confirm`, `owner`, `wrapper`, `model`, `ref` or null
 */
function initKind(init, names, sourceCode) {
    if (!init) return null
    if (init.type === 'Identifier') return names.get(init.name) ?? null
    if (init.type === 'MemberExpression') {
        const read = readPath(init)
        return read && names.get(read.root) === 'wrapper' ? 'wrapper' : null
    }
    if (init.type !== 'CallExpression' || init.callee.type !== 'Identifier') return null
    const name = init.callee.name
    if (CONFIRM_COMPOSABLES.has(name)) return 'confirm'
    if (CONFIRM_WRAPPERS.has(name)) return 'wrapper'
    if (name === 'defineModel') return 'model'
    if (name === 'ref') return 'ref'
    if (name === 'defineProps') return mentionsWrapper(sourceCode.getText(init)) ? 'wrapper' : null
    if (name === 'computed') return readsWrapper(sourceCode.getText(init), names) ? 'wrapper' : null
    return null
}

/**
 * Whether a piece of source names one of the wrapper composables, as a props type built from one does.
 *
 * @param text the source
 * @returns true when a wrapper is named
 */
function mentionsWrapper(text) {
    return [...CONFIRM_WRAPPERS].some(wrapper => new RegExp(`\\b${wrapper}\\b`).test(text))
}

/**
 * Whether a computed reads from a name already known to be a wrapper.
 *
 * @param text the computed's source
 * @param names what the names declared before it are bound to
 * @returns true when it does
 */
function readsWrapper(text, names) {
    return [...names].some(([name, kind]) => kind === 'wrapper' && new RegExp(`\\b${name}\\b`).test(text))
}

/**
 * The name an expression starts from and the properties read off it.
 *
 * @param expression a bound expression
 * @returns `{root, path}`, or null when the expression is not a plain read
 */
function readPath(expression) {
    const path = []
    let node = expression
    while (node) {
        if (node.type === 'TSNonNullExpression') node = node.expression
        else if (node.type === 'MemberExpression' && !node.computed) {
            path.unshift(node.property.name)
            node = node.object
        } else if (node.type === 'Identifier') return {root: node.name, path}
        else return null
    }
    return null
}

/**
 * The expression an element's model is bound to: `v-model` or `:model-value`.
 *
 * @param element a template element
 * @returns the expression, or undefined
 */
function modelBinding(element) {
    const bound = element.startTag.attributes.find(attribute => attribute.directive
        && ((attribute.key.name.name === 'model' && !attribute.key.argument)
            || (attribute.key.name.name === 'bind'
                && ['model-value', 'modelValue'].includes(attribute.key.argument?.name))))
    return bound?.value?.expression
}

/**
 * Whether a confirmation's model comes from where shift is heard.
 *
 * @param read the model's root and path
 * @param names what the script's names are bound to
 * @returns true for a confirmation composable's `show`, a wrapper's result, or a pass-through model
 */
function heardThrough(read, names) {
    const kind = names.get(read.root)
    if (kind === 'confirm' || kind === 'wrapper' || kind === 'model') return true
    return kind === 'owner' && read.path[0] === 'show'
}

/** Keys of an AST node that lead back up or sideways rather than into what it reads. */
const NOT_READ = new Set(['parent', 'loc', 'range', 'type', 'typeAnnotation', 'typeParameters'])

/**
 * Every name an expression reads, each with the properties read off it.
 *
 * @param expression a bound expression
 * @returns the reads, `{root, path}` each
 */
function readsOf(expression) {
    const reads = []
    const visit = node => {
        if (!node || typeof node.type !== 'string') return
        const read = node.type === 'Identifier' || node.type === 'MemberExpression' ? readPath(node) : null
        if (read) {
            reads.push(read)
            return
        }
        for (const [key, value] of Object.entries(node)) {
            if (NOT_READ.has(key)) continue
            if (key === 'property' && node.type === 'MemberExpression' && !node.computed) continue
            if (key === 'key' && node.type === 'Property' && !node.computed) continue
            if (Array.isArray(value)) value.forEach(visit)
            else if (value && typeof value === 'object') visit(value)
        }
    }
    visit(expression)
    return reads
}

/**
 * What decides whether an element is shown: its model where it has a real one, otherwise its `v-if`.
 *
 * @param element a template element
 * @returns the expression, or undefined when neither says anything
 */
function shownBy(element) {
    const model = modelBinding(element)
    if (model && model.type !== 'Literal') return model
    return directive(element, 'if')?.value?.expression
}

/**
 * Whether everything an element's showing depends on comes from where shift is heard.
 *
 * @param element a template element
 * @param names what the script's names are bound to
 * @returns true when nothing decides it, or every name read is heard through
 */
function shownThroughComposable(element, names) {
    const expression = shownBy(element)
    if (!expression) return true
    const reads = readsOf(expression)
    return reads.length > 0 && reads.every(read => heardThrough(read, names))
}

/**
 * Whether a plain `Modal` is a confirmation: a destructive button anywhere inside it.
 *
 * @param element a `Modal`
 * @returns true when it holds an error or delete button
 */
function isConfirmingModal(element) {
    let found = false
    walkElements(element, inner => {
        if (DESTRUCTIVE_BUTTON.test(inner.rawName)) found = true
    })
    return found
}

/**
 * A confirmation shown through anything but the composables that let shift skip it.
 *
 * <p>Holding shift skips a plain confirmation everywhere, and the skip lives in `request` of
 * `useConfirmAction` and `useConfirmDelete`. A dialog opened by setting a flag of its own never hears
 * of it, so a new one written that way quietly asks every time. A `ConfirmDeleteModal`, or a `Modal`
 * holding a destructive button, therefore has to take its model from one of those composables, from a
 * composable known to wrap one, or from the component's own `defineModel`, in which case the parent
 * opening it is held to the same: a component named like a confirmation is opened by nothing else
 * either, whether the flag is a `ref`, a computed or a comparison. Where the model is a literal, the
 * `v-if` mounting the dialog decides instead and is held to the same. Safety stops and dialogs taking a
 * reason, a note or a choice ask every time on purpose and say so with `data-confirm-always`.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Open confirmations through useConfirmAction or useConfirmDelete, so shift can skip them'},
        schema: [],
        messages: {
            byHand: 'This confirmation is opened by hand, so shift cannot skip it. Take its model from '
                + 'useConfirmAction or useConfirmDelete, or mark a dialog that must always ask with '
                + `${ALWAYS_ASKS}.`,
            openedByFlag: '<{{tag}}> is opened by a flag of this component, so shift cannot skip it. Open '
                + `it through useConfirmAction or useConfirmDelete, or mark it with ${ALWAYS_ASKS}.`,
        },
    },
    create(context) {
        const sourceCode = context.sourceCode
        return {
            Program(program) {
                const template = sourceCode.ast.templateBody
                if (!template) return
                const names = declaredNames(program, sourceCode)
                walkElements(template, element => {
                    if (staticAttribute(element, ALWAYS_ASKS) || directive(element, 'for')) return
                    const tag = element.rawName
                    const confirmation = tag === 'ConfirmDeleteModal' || (tag === 'Modal' && isConfirmingModal(element))
                    if (!confirmation && !CONFIRM_COMPONENT.test(tag)) return
                    if (shownThroughComposable(element, names)) return
                    if (confirmation) context.report({loc: element.startTag.loc, messageId: 'byHand'})
                    else context.report({loc: element.startTag.loc, messageId: 'openedByFlag', data: {tag}})
                })
            },
        }
    },
}

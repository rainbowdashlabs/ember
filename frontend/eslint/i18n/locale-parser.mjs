import {readFileSync, statSync} from 'node:fs'
import * as typescriptParser from '@typescript-eslint/parser'
import * as jsonc from 'jsonc-eslint-parser'

/**
 * Reads a TypeScript locale module the way `@intlify/eslint-plugin-vue-i18n` reads a JSON one.
 *
 * <p>The plugin's rules for locale files walk the AST `jsonc-eslint-parser` defines and look for
 * `parserServices.isJSON`, and they take no other shape. The locales here are modules exporting one
 * object literal, so this parses the module as TypeScript and hands ESLint that object converted
 * node by node into the JSON AST, every node keeping the range of the source it came from. Reports
 * therefore land on the real line of the `.ts` file.
 *
 * <p>A value may be a string, a concatenation of strings, a template literal without substitutions,
 * a number, a boolean or a nested object. An identifier naming a constant declared in the module
 * stands for that constant's value, which is how the refusals share one sentence between codes. An
 * identifier naming anything else, such as an imported block merged in under its key, becomes an
 * empty object: it is a block that lives in a file of its own and is read there.
 *
 * <p>ESLint gets nothing but this AST for such a file, so a config must hand the locale files to
 * this parser alone and run only rules written for the JSON AST on them.
 *
 * @param code the module's source
 * @returns what ESLint expects of a parser
 */
export function parseForESLint(code) {
    const program = typescriptParser.parse(code, {range: true, loc: true, tokens: true, comment: true, sourceType: 'module'})
    const exported = program.body.find(statement => statement.type === 'ExportDefaultDeclaration')
    const constants = moduleConstants(program)
    const expression = exported
        ? convert(unwrap(exported.declaration), constants)
        : {type: 'JSONObjectExpression', properties: [], range: program.range, loc: program.loc}
    const statement = {type: 'JSONExpressionStatement', expression, range: expression.range, loc: expression.loc}
    return {
        ast: {
            type: 'Program',
            body: [statement],
            comments: program.comments,
            tokens: program.tokens,
            range: [0, code.length],
            loc: {start: {line: 1, column: 0}, end: program.loc.end},
        },
        visitorKeys: jsonc.VisitorKeys,
        services: {isJSON: true},
    }
}

/** Parser metadata ESLint prints and caches by. */
export const meta = {name: 'ember-locale-parser'}

const cache = new Map()

/**
 * The messages a locale module defines, as a plain object, read from disk and kept until the file
 * changes.
 *
 * @param file the absolute path of the module
 * @returns its messages
 */
export function localeMessagesOf(file) {
    const modified = statSync(file).mtimeMs
    const cached = cache.get(file)
    if (cached && cached.modified === modified) return cached.messages
    const messages = messagesOf(parseForESLint(readFileSync(file, 'utf-8')).ast)
    cache.set(file, {modified, messages})
    return messages
}

/**
 * The messages the JSON AST of a locale module describes.
 *
 * @param ast the program this parser produced
 * @returns the messages as a plain object
 */
export function messagesOf(ast) {
    return jsonc.getStaticJSONValue(ast.body[0].expression)
}

/**
 * Every leaf key of a message tree as a dotted path.
 *
 * @param messages the tree
 * @param prefix the path the tree sits under
 * @returns the dotted paths of its messages
 */
export function leafKeys(messages, prefix = '') {
    const keys = []
    for (const [name, value] of Object.entries(messages)) {
        const key = prefix ? `${prefix}.${name}` : name
        if (typeof value === 'object' && value !== null) keys.push(...leafKeys(value, key))
        else keys.push(key)
    }
    return keys
}

/**
 * The constants declared at the top of a module, by name, with the expression each is set to.
 *
 * @param program the parsed module
 * @returns the declarations
 */
function moduleConstants(program) {
    const constants = new Map()
    for (const statement of program.body) {
        if (statement.type !== 'VariableDeclaration' || statement.kind !== 'const') continue
        for (const declarator of statement.declarations) {
            if (declarator.id.type === 'Identifier' && declarator.init) constants.set(declarator.id.name, declarator.init)
        }
    }
    return constants
}

/**
 * An expression without the type assertions around it, which carry no message.
 *
 * @param node the expression
 * @returns the expression inside
 */
function unwrap(node) {
    let current = node
    while (current.type === 'TSAsExpression' || current.type === 'TSSatisfiesExpression') current = current.expression
    return current
}

/**
 * One TypeScript expression as the JSON AST node that stands for it.
 *
 * @param node the expression
 * @param constants the module's constants, for identifiers that name one
 * @returns a new JSON AST node carrying the expression's range
 */
function convert(node, constants) {
    const at = {range: node.range, loc: node.loc}
    switch (node.type) {
        case 'ObjectExpression':
            return {type: 'JSONObjectExpression', properties: node.properties.filter(isPlainProperty).map(property => convertProperty(property, constants)), ...at}
        case 'ArrayExpression':
            return {type: 'JSONArrayExpression', elements: node.elements.map(element => element && convert(element, constants)), ...at}
        case 'Literal':
            return {type: 'JSONLiteral', value: node.value, raw: node.raw, ...at}
        case 'TemplateLiteral':
            return node.expressions.length === 0
                ? {type: 'JSONTemplateLiteral', quasis: node.quasis.map(quasi => ({type: 'JSONTemplateElement', value: quasi.value, tail: quasi.tail, range: quasi.range, loc: quasi.loc})), expressions: [], ...at}
                : emptyObject(at)
        case 'BinaryExpression':
            return {type: 'JSONBinaryExpression', operator: node.operator, left: convert(node.left, constants), right: convert(node.right, constants), ...at}
        case 'Identifier':
            return constants.has(node.name) ? convert(unwrap(constants.get(node.name)), constants) : emptyObject(at)
        case 'TSAsExpression':
        case 'TSSatisfiesExpression':
            return convert(unwrap(node), constants)
        default:
            return emptyObject(at)
    }
}

/**
 * Whether an object member is a plain `key: value` pair, which is all a message tree holds.
 *
 * @param property the member
 * @returns false for spreads, methods and computed keys
 */
function isPlainProperty(property) {
    return property.type === 'Property' && !property.computed && property.kind === 'init' && !property.method
}

/**
 * One property as a JSON AST property.
 *
 * @param property the TypeScript property
 * @param constants the module's constants
 * @returns the JSON AST property
 */
function convertProperty(property, constants) {
    const key = property.key.type === 'Identifier'
        ? {type: 'JSONIdentifier', name: property.key.name, range: property.key.range, loc: property.key.loc}
        : {type: 'JSONLiteral', value: property.key.value, raw: property.key.raw, range: property.key.range, loc: property.key.loc}
    return {
        type: 'JSONProperty',
        kind: 'init',
        computed: false,
        method: false,
        shorthand: false,
        key,
        value: convert(property.value, constants),
        range: property.range,
        loc: property.loc,
    }
}

/**
 * An empty object standing where a value is not a message this file defines.
 *
 * @param at the range and location it takes
 * @returns the node
 */
function emptyObject(at) {
    return {type: 'JSONObjectExpression', properties: [], ...at}
}

import {readFileSync} from 'node:fs'
import {join} from 'node:path'
import {SRC} from './vue-template.mjs'

/** The file the API generator writes, which carries a constant for every string enum of the backend. */
const SCHEMA = join(SRC, 'api', 'generated', 'schema.ts')

/**
 * The hand-kept enum constants that stay, each with the reason the generated one does not serve.
 *
 * <p>All of them mirror a backend enum whose schema name is a bare word (`Mode`, `Status`, `Match`),
 * because the enum is nested in a class and the class name does not reach the API description.
 * Imported under that name it would say nothing about what it is. An entry leaves once the backend
 * gives the enum a name of its own.
 */
const ALLOWED = new Map([
    ['CapabilityDirection', "generated as 'Direction'"],
    ['ImportStatus', "generated as 'Status'"],
    ['LendingEmptyReason', "generated as 'EmptyReason'"],
    ['PageTargetKind', "generated as 'TargetKind'"],
    ['PasskeyMode', "generated as 'Mode'"],
    ['QuizAttemptStatus', "generated as 'AttemptStatus'"],
    ['QuizTestStatus', "generated as 'TestStatus'"],
    ['ResultDimension', "generated as 'Dimension'"],
    ['ResultMatch', "generated as 'Match'"],
    ['StoragePlacementActual', "generated as 'Actual'"],
    ['StoragePlacementExpected', "generated as 'Expected'"],
])

const GUIDE = 'See Enums in frontend/CLAUDE.md.'

let generated = null

/**
 * The names of the enum constants the generator writes, read once per process.
 *
 * @returns the constant names
 */
function generatedConstants() {
    generated ??= new Set([...readFileSync(SCHEMA, 'utf-8').matchAll(/^export const (\w+) = \{/gm)].map(match => match[1]))
    return generated
}

/**
 * Hand-kept copies of the enum constants the API generator already writes.
 *
 * <p>For every string enum of the backend, `src/api/generated/schema.ts` exports the union type and a
 * constant of its values under one name. A copy kept by hand drifts from the backend the day a value
 * is added, so the rule refuses the two shapes such a copy takes:
 *
 * <ul>
 *   <li>`const X = {...} as const satisfies Record<...>` where the generator writes a constant `X`;</li>
 *   <li>a type alias `XName` of a generated enum (`components['schemas']['...']` or `Schemas['...']`)
 *       in a file that declares a constant `X` of its own.</li>
 * </ul>
 *
 * <p>The few constants that stay are listed with their reason in {@link ALLOWED}.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Forbid hand-kept copies of the generated enum constants'},
        schema: [],
        messages: {
            copy: `'{{name}}' is generated: import it from '@/api/generated/schema' instead of keeping a copy. ${GUIDE}`,
            alias: `'{{alias}}' renames the generated enum '{{schema}}' for a hand-kept '{{name}}'. Import the generated constant and type from '@/api/generated/schema' instead. ${GUIDE}`,
        },
    },
    create(context) {
        const known = generatedConstants()
        const constants = new Set()
        const aliases = []
        return {
            VariableDeclarator(node) {
                if (node.id.type !== 'Identifier' || node.parent.kind !== 'const') return
                constants.add(node.id.name)
                if (isConstRecord(node.init) && known.has(node.id.name) && !ALLOWED.has(node.id.name)) {
                    context.report({node: node.id, messageId: 'copy', data: {name: node.id.name}})
                }
            },
            TSTypeAliasDeclaration(node) {
                const schema = schemaEnumOf(node.typeAnnotation)
                if (node.id.name.endsWith('Name') && schema && known.has(schema)) aliases.push({node, schema})
            },
            'Program:exit'() {
                for (const {node, schema} of aliases) {
                    const name = node.id.name.slice(0, -'Name'.length)
                    if (!constants.has(name) || ALLOWED.has(name)) continue
                    context.report({node: node.id, messageId: 'alias', data: {alias: node.id.name, schema, name}})
                }
            },
        }
    },
}

/**
 * Whether an initialiser is an object asserted `as const` and checked against a `Record`.
 *
 * @param init the initialiser
 * @returns true for `{...} as const satisfies Record<...>`
 */
function isConstRecord(init) {
    if (init?.type !== 'TSSatisfiesExpression' || referenceName(init.typeAnnotation) !== 'Record') return false
    const asserted = init.expression
    return asserted.type === 'TSAsExpression' && referenceName(asserted.typeAnnotation) === 'const'
        && asserted.expression.type === 'ObjectExpression'
}

/**
 * The schema name an indexed type reads, such as `FormStatus` for `components['schemas']['FormStatus']`.
 *
 * @param type a type node
 * @returns the name, or null for any other type
 */
function schemaEnumOf(type) {
    if (type.type !== 'TSIndexedAccessType' || type.indexType.type !== 'TSLiteralType') return null
    const value = type.indexType.literal.value
    return typeof value === 'string' && readsSchemas(type.objectType) ? value : null
}

/**
 * Whether a type is the generated schema table, either `components['schemas']` or a `Schemas` alias.
 *
 * @param type a type node
 * @returns true for either form
 */
function readsSchemas(type) {
    if (referenceName(type) === 'Schemas') return true
    return type.type === 'TSIndexedAccessType' && referenceName(type.objectType) === 'components'
        && type.indexType.type === 'TSLiteralType' && type.indexType.literal.value === 'schemas'
}

/**
 * The name of a type reference, such as `Record` for `Record<A, B>`.
 *
 * @param type a type node
 * @returns the name, or null for any other type
 */
function referenceName(type) {
    return type?.type === 'TSTypeReference' && type.typeName.type === 'Identifier' ? type.typeName.name : null
}

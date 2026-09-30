import {resolve} from 'node:path'
import {germanKeys, localeFiles, ownerOf, propertyAt} from './catalogue.mjs'
import {enumConstants, javaSource, keyLiterals, refusalCodes} from './java.mjs'

/** How a section's constants are read from its Java files. */
const READERS = {
    constants: enumConstants,
    'refusal-codes': refusalCodes,
}

/**
 * Holds the German messages to what the backend declares.
 *
 * <p>Some sections of the locale carry one entry per constant of a backend enum and are only ever
 * reached through a key built at runtime, which no usage scan can follow. So each such section is
 * read against the enum itself: every constant needs all its leaves (`leaves`, or the constant
 * itself for a plain label map), and an upper-case entry no constant matches is stale. A section may
 * mirror several enums, as the permissions mirror the station's and the cluster's. `reader` says how
 * the names are read where they are not the constants themselves; the refusals are keyed by code.
 *
 * <p>The files in `keyFiles` hand keys to the frontend as data, and a key they send that German
 * does not define is a missing translation.
 *
 * <p>Each finding is reported in the German file its key belongs to. A Java file that cannot be
 * found is an error too: it means a rename nobody followed here.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'require the translations the backend relies on, and no stale ones'},
        schema: [{
            type: 'object',
            properties: {
                german: {
                    type: 'array',
                    items: {
                        type: 'object',
                        properties: {file: {type: 'string'}, prefix: {type: 'string'}},
                        required: ['file'],
                        additionalProperties: false,
                    },
                },
                sections: {
                    type: 'array',
                    items: {
                        type: 'object',
                        properties: {
                            enumFiles: {type: 'array', items: {type: 'string'}},
                            prefix: {type: 'string'},
                            leaves: {type: 'array', items: {type: 'string'}},
                            reader: {enum: Object.keys(READERS)},
                        },
                        required: ['enumFiles', 'prefix'],
                        additionalProperties: false,
                    },
                },
                keyFiles: {type: 'array', items: {type: 'string'}},
            },
            required: ['german'],
            additionalProperties: false,
        }],
        messages: {
            missingSource: 'Backend source not found: {{file}}.',
            missingConstant: "Missing translation for enum value {{constant}}: '{{key}}'.",
            staleConstant: "Stale {{prefix}} entry {{constant}}: no enum value matches it.",
            missingSent: "i18n key '{{key}}' sent by {{file}} is not defined.",
        },
    },
    create(context) {
        const options = context.options[0]
        const {german} = localeFiles(context, options)
        const current = resolve(context.filename)
        const entry = german.find(candidate => candidate.file === current)
        if (!entry) return {}

        return {
            'Program:exit'(program) {
                const defined = germanKeys(german, current, program)
                const fallback = program.body[0].expression
                const nodeFor = key => propertyAt(program, entry.prefix, key)?.key ?? fallback
                for (const section of options.sections ?? []) {
                    if (ownerOf(german, section.prefix) === entry) checkSection(context, section, defined, nodeFor, fallback)
                }
                if (entry.prefix === '') checkSentKeys(context, options.keyFiles ?? [], defined, fallback)
            },
        }
    },
}

/**
 * Holds one enum-backed section to its enums.
 *
 * @param context the rule context
 * @param section the section's options
 * @param defined every German key
 * @param nodeFor where a key of this file is reported
 * @param fallback where a finding without a property of its own is reported
 */
function checkSection(context, section, defined, nodeFor, fallback) {
    const read = READERS[section.reader ?? 'constants']
    const constants = new Set()
    for (const file of section.enumFiles) {
        const text = javaSource(resolve(context.cwd, file))
        if (text === null) {
            context.report({node: fallback, messageId: 'missingSource', data: {file}})
            continue
        }
        for (const constant of read(text)) constants.add(constant)
    }

    for (const constant of constants) {
        const keys = section.leaves
            ? section.leaves.map(leaf => `${section.prefix}.${constant}.${leaf}`)
            : [`${section.prefix}.${constant}`]
        for (const key of keys) {
            if (!defined.has(key)) context.report({node: nodeFor(section.prefix), messageId: 'missingConstant', data: {constant, key}})
        }
    }

    const reported = new Set()
    for (const key of defined) {
        if (!key.startsWith(`${section.prefix}.`)) continue
        const constant = key.slice(section.prefix.length + 1).split('.')[0]
        if (!/^[A-Z][A-Z0-9_-]*$/.test(constant) || constants.has(constant) || reported.has(constant)) continue
        reported.add(constant)
        context.report({node: nodeFor(`${section.prefix}.${constant}`), messageId: 'staleConstant', data: {prefix: section.prefix, constant}})
    }
}

/**
 * Requires a translation for every key the backend sends as data.
 *
 * @param context the rule context
 * @param files the Java files that send keys
 * @param defined every German key
 * @param node where the findings are reported
 */
function checkSentKeys(context, files, defined, node) {
    const namespaces = new Set([...defined].map(key => key.split('.')[0]))
    for (const file of files) {
        const text = javaSource(resolve(context.cwd, file))
        if (text === null) {
            context.report({node, messageId: 'missingSource', data: {file}})
            continue
        }
        for (const key of keyLiterals(text)) {
            if (namespaces.has(key.split('.')[0]) && !defined.has(key)) {
                context.report({node, messageId: 'missingSent', data: {key, file}})
            }
        }
    }
}

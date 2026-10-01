import {readFileSync} from 'node:fs'
import {dirname, join} from 'node:path'

/**
 * The source of a Java file, or null where it does not exist.
 *
 * <p>What the backend declares is read from its Java sources as text. These are the only reads of
 * raw source among the translation rules, and they read Java, which no parser here understands.
 *
 * @param file the absolute path
 * @returns the text
 */
export function javaSource(file) {
    try {
        return readFileSync(file, 'utf-8')
    } catch {
        return null
    }
}

/**
 * The constant names of a Java enum. Comments are stripped, the constant list ends at the first
 * semicolon, and names inside parentheses or braces (constructor arguments, constant bodies) are
 * left out so that only top-level declarations count.
 *
 * @param text the enum's source
 * @returns the constants
 */
export function enumConstants(text) {
    const stripped = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*/g, '')
    const body = stripped.slice(stripped.indexOf('{') + 1).split(';')[0]
    const constants = new Set()
    let depth = 0
    for (const token of body.matchAll(/([A-Z][A-Z0-9_]*)|([({])|([)}])/g)) {
        if (token[2]) depth++
        else if (token[3]) depth--
        else if (depth === 0) constants.add(token[1])
    }
    return constants
}

/**
 * The codes of the refusals one area's enum declares.
 *
 * <p>Refusals are keyed by their code, which is the area's one- or two-letter prefix and the
 * constant's number. Each area enum names its area once, where it builds its definition; the
 * prefixes are declared by the area registry in `Refusal.java` beside it, so both are read to put
 * a code together.
 *
 * @param text the source of one area's enum
 * @param file the absolute path of that source
 * @returns every code it declares, as the locale writes them
 */
export function refusalCodes(text, file) {
    const area = text.match(/new Definition\(Area\.([A-Z_]+),/)?.[1]
    const registry = javaSource(join(dirname(file), 'Refusal.java')) ?? ''
    const prefix = registry.match(new RegExp(`^\\s{8}${area}\\("([A-Z]{1,2})",`, 'm'))?.[1]
    const codes = new Set()
    if (!prefix) return codes
    for (const match of text.matchAll(/^\s{4}[A-Z][A-Z0-9_]*\(\s*(\d+),/gm)) {
        codes.add(`${prefix}-${String(match[1]).padStart(3, '0')}`)
    }
    return codes
}

/**
 * Every string literal of a Java source that is spelled like a translation key.
 *
 * @param text the source
 * @returns the dotted strings
 */
export function keyLiterals(text) {
    return [...text.matchAll(/"([a-zA-Z][\w-]*(?:\.[\w-]+)+)"/g)].map(match => match[1])
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {readdirSync, readFileSync, statSync} from 'node:fs'
import {join, relative} from 'node:path'
import {describe, expect, it} from 'vitest'

/**
 * Every CSS custom property the sources read is one they also define.
 *
 * <p>A variable nobody defines does not fail anywhere: the browser drops the declaration and the
 * element quietly loses its colour. That is how fifty-odd places asked for a primary colour under a
 * name the theme never had, and nothing said so.
 *
 * <p>A definition counts wherever it is written: the theme and the root rules in `style.css`, a
 * scoped style block, or the variables the palette sets on the page at runtime. A use is a
 * `var(--name)` or a Tailwind arbitrary value such as `text-(--name)`.
 */
const SRC = join(import.meta.dirname, '..')

/** Variables a library sets on its own elements at runtime, so no source file defines them. */
const LIBRARY_PREFIXES = ['--reka-']

const DEFINITION = /(?:^|[\s{;'"`])(--[a-z][\w-]*)['"`]?\s*:/gm
const RUNTIME_DEFINITION = /setProperty\(\s*['"`](--[a-z][\w-]*)/g
const USE = /\(\s*(--[a-z][\w-]*)\s*[),]/g

function sourceFiles(dir: string): string[] {
    const found: string[] = []
    for (const entry of readdirSync(dir)) {
        const path = join(dir, entry)
        if (statSync(path).isDirectory()) found.push(...sourceFiles(path))
        else if (/\.(vue|ts|css)$/.test(entry) && !/\.(test|spec)\.ts$/.test(entry)) found.push(path)
    }
    return found
}

function names(content: string, pattern: RegExp): string[] {
    return [...content.matchAll(pattern)].map(match => match[1]!)
}

/** Each variable read somewhere but defined nowhere, with the files that read it. */
function undefinedVariables(): Map<string, string[]> {
    const defined = new Set<string>()
    const readers = new Map<string, string[]>()
    for (const file of sourceFiles(SRC)) {
        const content = readFileSync(file, 'utf-8')
        names(content, DEFINITION).forEach(name => defined.add(name))
        names(content, RUNTIME_DEFINITION).forEach(name => defined.add(name))
        for (const name of new Set(names(content, USE))) {
            readers.set(name, [...(readers.get(name) ?? []), relative(SRC, file)])
        }
    }
    return new Map([...readers].filter(([name]) =>
        !defined.has(name) && !LIBRARY_PREFIXES.some(prefix => name.startsWith(prefix))))
}

describe('CSS custom properties', () => {
    const missing = undefinedVariables()

    it('reads no variable that is defined nowhere', () => {
        expect(Object.fromEntries(missing)).toEqual({})
    })
})

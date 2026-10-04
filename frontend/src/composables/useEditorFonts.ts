/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, onScopeDispose, provide, shallowRef, useId, watch, type ComputedRef, type InjectionKey} from 'vue'
import {FontStyle, type DocumentFontsResponse} from '@/api/generated/schema'
import type {FontFileLoader} from '@/api/documentFonts'
import {familyKey, FONT_STYLES, reachedFamily} from '@/components/documents/fonts/fontOptions'

/** What the template editor shows its text in, for the parts of it below. */
export interface EditorFonts {
    /** What every element whose text shows in the template's fonts carries as `data-editor-fonts`. */
    scope: string
    /**
     * The CSS `font-family` for text in a family the template names, or null for the default font, or
     * nothing where that family shows in the editor's own font.
     */
    fontFamily(family: string | null): string | undefined
    /** Whether words set in a family show in it. */
    shown(family: string): boolean
}

/** The attributes an area of the template carries so its text shows in the template's fonts. */
export interface FontAreaAttrs {
    'data-editor-fonts'?: string
    style?: Record<string, string>
}

/** One family the browser holds for the editor, under the name it was registered by. */
interface LoadedFamily {
    spelled: string | null
    cssName: string
}

/** What is loaded for one family: which of its files, and under which version. */
interface FamilyRequest {
    family: string | null
    styles: readonly FontStyle[]
    version: string
}

const EDITOR_FONTS: InjectionKey<EditorFonts> = Symbol('editorFonts')
const FONT_AREA: InjectionKey<ComputedRef<FontAreaAttrs>> = Symbol('editorFontArea')

/** The file a style is drawn from, as a document prints the family: that style, else the regular one. */
function fileStyle(style: FontStyle, styles: readonly FontStyle[]): FontStyle {
    if (styles.includes(style)) return style
    return styles.includes(FontStyle.REGULAR) ? FontStyle.REGULAR : styles[0] ?? FontStyle.REGULAR
}

function descriptorsOf(style: FontStyle): FontFaceDescriptors {
    const bold = style === FontStyle.BOLD || style === FontStyle.BOLD_ITALIC
    const italic = style === FontStyle.ITALIC || style === FontStyle.BOLD_ITALIC
    return {weight: bold ? '700' : '400', style: italic ? 'italic' : 'normal'}
}

/** Quotes a text for a CSS string, as an attribute value or a family name. */
function cssString(text: string): string {
    return `"${text.replace(/["\\]/g, '\\$&').replace(/\n/g, ' ')}"`
}

/**
 * What the browser is asked to load for a family the template reaches, or for the default font, or null
 * where neither can be loaded.
 */
function requestOf(list: DocumentFontsResponse, family: string | null): FamilyRequest | null {
    const option = reachedFamily(list.reachable, family)
    if (option?.editorVersion) return {family: option.family, styles: option.styles, version: option.editorVersion}
    if (option || list.defaultStyles.length === 0) return null
    return {family: null, styles: list.defaultStyles, version: `${list.defaultFamily}:${list.defaultStyles.join(',')}`}
}

function canRegister(): boolean {
    return typeof FontFace !== 'undefined' && typeof document !== 'undefined' && document.fonts !== undefined
}

/**
 * Shows a template's text in the fonts it prints in, for as long as its editor is open. This is the one
 * place font files reach a browser.
 *
 * <p>The families the template uses are loaded as they come up, each once: the fonts of its page and
 * those words are set in, and a family picked for the first time. A family a template names but no
 * longer reaches is shown as the default font it prints in. Each family is registered with the browser's
 * `FontFace` API under a name of this editor's own, which nothing outside it names, and a stylesheet
 * scoped to elements carrying {@link EditorFonts.scope} sets the text in it: the page's font where an
 * area says so ({@link useEditorFontArea}), words in the family they are set in. A style the family lacks
 * is drawn from its regular file, as a document draws it.
 *
 * <p>A family whose files cannot be loaded (one Typst carries inside itself, a default font without web
 * files, a file that is gone) keeps the editor's own look, and words set in it keep their marker. When
 * the editor closes, the faces leave the browser's font set and the stylesheet the page.
 *
 * @param load the owner's font file loader, or null where it has none and every family keeps the editor's look
 * @param list the families the owner reaches and the default font, null until they are read
 * @param used the families the template names, null for the default font
 */
export function useEditorFonts(
    load: FontFileLoader | null,
    list: () => DocumentFontsResponse | null,
    used: () => readonly (string | null)[],
): EditorFonts {
    const scope = `ef${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
    const loaded = shallowRef<ReadonlyMap<string, LoadedFamily>>(new Map())
    const asked = new Set<string>()
    const registered: FontFace[] = []
    let sheet: HTMLStyleElement | null = null
    let disposed = false

    function writeSheet() {
        if (!sheet) {
            sheet = document.createElement('style')
            sheet.dataset.editorFonts = scope
            document.head.appendChild(sheet)
        }
        const area = `[data-editor-fonts=${cssString(scope)}]`
        const rules = [`${area} .markdown-content { font-family: var(--editor-page-font, inherit); }`]
        for (const family of loaded.value.values()) {
            if (family.spelled === null) continue
            const word = `[data-font=${cssString(family.spelled)} i]`
            rules.push(`${area} ${word}, ${area} .tiptap .text-font${word} { font-family: ${cssString(family.cssName)}, sans-serif; `
                + 'text-decoration: none; cursor: inherit; }')
        }
        sheet.textContent = rules.join('\n')
    }

    async function register(key: string, request: FamilyRequest, spelled: string | null) {
        if (!load) return
        const cssName = `ember-editor-${scope}-${asked.size}`
        const files = new Map<FontStyle, ArrayBuffer>()
        for (const style of request.styles) files.set(style, await load(request.family, style, request.version))
        const faces = FONT_STYLES.map(style =>
            new FontFace(cssName, files.get(fileStyle(style, request.styles)) ?? new ArrayBuffer(0), descriptorsOf(style)))
        await Promise.all(faces.map(face => face.load()))
        if (disposed) return
        faces.forEach(face => document.fonts.add(face))
        registered.push(...faces)
        loaded.value = new Map([...loaded.value, [key, {spelled, cssName}]])
        writeSheet()
    }

    function ask(family: string | null) {
        const current = list()
        if (!current) return
        if (family !== null && !reachedFamily(current.reachable, family)) {
            ask(null)
            return
        }
        const key = familyKey(family)
        if (asked.has(key)) return
        asked.add(key)
        const request = requestOf(current, family)
        if (request) register(key, request, family).catch(() => undefined)
    }

    if (canRegister() && load) {
        watch([list, used], () => used().forEach(ask), {immediate: true})
    }

    onScopeDispose(() => {
        disposed = true
        registered.forEach(face => document.fonts.delete(face))
        sheet?.remove()
    })

    function find(family: string | null): LoadedFamily | undefined {
        const own = loaded.value.get(familyKey(family))
        if (own || family === null) return own
        const current = list()
        return current && !reachedFamily(current.reachable, family) ? loaded.value.get('') : undefined
    }

    const fonts: EditorFonts = {
        scope,
        fontFamily(family) {
            const found = find(family)
            return found ? `${cssString(found.cssName)}, sans-serif` : undefined
        },
        shown(family) {
            return loaded.value.has(familyKey(family))
        },
    }
    provide(EDITOR_FONTS, fonts)
    return fonts
}

/**
 * Marks an area of the template, such as the body or the header, whose text shows in a family of the
 * page, and hands the same to the editors below it, which a dialog may lift out of the area.
 *
 * @param family the family the area's text prints in, null for the default font
 * @returns the attributes the area's element carries; none outside the template editor
 */
export function useEditorFontArea(family: () => string | null): ComputedRef<FontAreaAttrs> {
    const fonts = inject(EDITOR_FONTS, null)
    const attrs = computed<FontAreaAttrs>(() => {
        if (!fonts) return {}
        const css = fonts.fontFamily(family())
        return {'data-editor-fonts': fonts.scope, ...(css ? {style: {'--editor-page-font': css}} : {})}
    })
    provide(FONT_AREA, attrs)
    return attrs
}

/** The attributes of the template area a text editor writes in, or null outside the template editor. */
export function useEditorFontAreaAttrs(): ComputedRef<FontAreaAttrs> | null {
    return inject(FONT_AREA, null)
}

/** The fonts of the template editor around, or null outside it, where words keep the editor's look. */
export function useInjectedEditorFonts(): EditorFonts | null {
    return inject(EDITOR_FONTS, null)
}

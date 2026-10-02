/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A label and the value printed beside it. */
export interface SheetLine {
    label: string
    value: string
}

/** What a printed sheet holds: a heading, its lines and a closing note. */
export interface Sheet {
    title: string
    lines: readonly SheetLine[]
    note: string
}

const SHEET_STYLE = `
body { font-family: system-ui, sans-serif; margin: 2.5rem; color: #111; }
h1 { font-size: 1.4rem; margin-bottom: 1.5rem; }
dt { font-size: 0.8rem; text-transform: uppercase; color: #555; margin-top: 1rem; }
dd { font-size: 1.2rem; margin: 0.2rem 0 0; font-family: ui-monospace, monospace; }
p { margin-top: 2rem; font-size: 0.95rem; }`

/**
 * The sheet as a document of its own: the text goes in as text, never as markup, so a name with
 * angle brackets in it prints as it reads.
 */
function fill(document: Document, sheet: Sheet) {
    document.title = sheet.title
    const style = document.createElement('style')
    style.textContent = SHEET_STYLE
    document.head.append(style)
    const heading = document.createElement('h1')
    heading.textContent = sheet.title
    const list = document.createElement('dl')
    for (const line of sheet.lines) {
        const label = document.createElement('dt')
        label.textContent = line.label
        const value = document.createElement('dd')
        value.textContent = line.value
        list.append(label, value)
    }
    const note = document.createElement('p')
    note.textContent = sheet.note
    document.body.append(heading, list, note)
}

/**
 * Prints a short sheet on its own, without the page and the dialog it was asked from.
 *
 * <p>The sheet is laid out in a frame nobody sees, printed from there and the frame removed once the
 * print dialog has closed, so nothing about it outlives the moment and no window is left open.
 */
export function printSheet(sheet: Sheet): void {
    const frame = document.createElement('iframe')
    frame.setAttribute('aria-hidden', 'true')
    frame.style.position = 'fixed'
    frame.style.width = '0'
    frame.style.height = '0'
    frame.style.border = '0'
    document.body.append(frame)
    const view = frame.contentWindow
    if (!view) {
        frame.remove()
        return
    }
    fill(view.document, sheet)
    view.addEventListener('afterprint', () => frame.remove())
    view.focus()
    view.print()
}

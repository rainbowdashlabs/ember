/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FontStyle, PdfFieldKind, SignatureRole, TextAlign, type PdfField} from '@/api/generated/schema'
import {freeSignerOf} from '@/components/documents/signers'
import {fieldRectOf, keptOnCanvas, scaleOf, screenBoxOf, type PageGeometry, type ScreenBox} from './pdfViewport'

/** How large a new field is, in points as the reader sees the page. */
const NEW_SIZE: Readonly<Record<PdfFieldKind, {width: number; height: number}>> = {
    [PdfFieldKind.TEXT]: {width: 160, height: 16},
    [PdfFieldKind.CHECK]: {width: 12, height: 12},
    [PdfFieldKind.SIGNATURE]: {width: 170, height: 40},
    [PdfFieldKind.FILL_IN]: {width: 160, height: 18},
}

/** The text size a new text field starts at, in points. */
export const DEFAULT_FONT_SIZE = 10

/** The first signer whose field asks nobody already asked to sign, or null where none is left. */
export function freeSigner(fields: readonly PdfField[]): SignatureRole | null {
    return freeSignerOf(fields.filter(field => field.kind === PdfFieldKind.SIGNATURE).map(field => field.role))
}

/** The signer of the first signature field, who a new field to fill in goes with, or null where none is placed. */
export function firstSigner(fields: readonly PdfField[]): SignatureRole | null {
    return fields.find(field => field.kind === PdfFieldKind.SIGNATURE && field.role)?.role ?? null
}

/**
 * A new field in the middle of the part of the page in view, upright to the reader whichever way the
 * page is turned. Zoomed in, that is the part scrolled to, so the field lands where the reader is
 * looking rather than somewhere off screen; without a view it is the middle of the whole page. A
 * signature field takes the first signer without one, a field to fill in the signer of the first
 * signature field, or the participant where none is placed yet.
 */
export function newField(
    kind: PdfFieldKind,
    page: number,
    geometry: PageGeometry,
    fields: readonly PdfField[],
    view: ScreenBox = {left: 0, top: 0, width: geometry.width, height: geometry.height},
): PdfField {
    const scale = scaleOf(geometry)
    const size = NEW_SIZE[kind]
    const box = keptOnCanvas(geometry, {
        left: view.left + (view.width - size.width * scale) / 2,
        top: view.top + (view.height - size.height * scale) / 2,
        width: size.width * scale,
        height: size.height * scale,
    })
    const signature = kind === PdfFieldKind.SIGNATURE
    return {
        kind,
        rect: fieldRectOf(geometry, page, box),
        text: signature ? null : '',
        fontSize: DEFAULT_FONT_SIZE,
        align: TextAlign.LEFT,
        wrap: false,
        role: roleOf(kind, fields),
        fontFamily: null,
        fontStyle: FontStyle.REGULAR,
        withoutLine: false,
        printText: false,
        statement: null,
        required: false,
        maxLength: null,
    }
}

function roleOf(kind: PdfFieldKind, fields: readonly PdfField[]): SignatureRole | null {
    if (kind === PdfFieldKind.SIGNATURE) return freeSigner(fields)
    if (kind === PdfFieldKind.FILL_IN) return firstSigner(fields) ?? SignatureRole.PARTICIPANT
    return null
}

/** A field with its box changed on the canvas, kept on the page. */
export function withBox(geometry: PageGeometry, field: PdfField, change: (box: ScreenBox) => ScreenBox): PdfField {
    const box = keptOnCanvas(geometry, change(screenBoxOf(geometry, field.rect)))
    return {...field, rect: fieldRectOf(geometry, field.rect.page, box)}
}

/** A field moved on the canvas by some CSS pixels. */
export function movedBy(geometry: PageGeometry, field: PdfField, dx: number, dy: number): PdfField {
    return withBox(geometry, field, box => ({...box, left: box.left + dx, top: box.top + dy}))
}

/** A field made larger or smaller on the canvas by some CSS pixels, its top left staying where it is. */
export function resizedBy(geometry: PageGeometry, field: PdfField, dw: number, dh: number): PdfField {
    return withBox(geometry, field, box => ({...box, width: box.width + dw, height: box.height + dh}))
}

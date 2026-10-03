/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FontStyle, PdfFieldKind, SignatureRole, TextAlign, type PdfField} from '@/api/generated/schema'
import {fieldRectOf, keptOnCanvas, scaleOf, screenBoxOf, type PageGeometry, type ScreenBox} from './pdfViewport'

/** The signers in the order a new signature field takes the first free one. */
export const SIGNERS: readonly SignatureRole[] = [
    SignatureRole.PARTICIPANT,
    SignatureRole.GUARDIAN_1,
    SignatureRole.GUARDIAN_2,
    SignatureRole.ISSUER,
]

/** How large a new field is, in points as the reader sees the page. */
const NEW_SIZE: Readonly<Record<PdfFieldKind, {width: number; height: number}>> = {
    [PdfFieldKind.TEXT]: {width: 160, height: 16},
    [PdfFieldKind.CHECK]: {width: 12, height: 12},
    [PdfFieldKind.SIGNATURE]: {width: 170, height: 40},
}

/** The text size a new text field starts at, in points. */
export const DEFAULT_FONT_SIZE = 10

/** The first signer without a signature field yet, or null where every one has one. */
export function freeSigner(fields: readonly PdfField[]): SignatureRole | null {
    return SIGNERS.find(role => !fields.some(field => field.role === role)) ?? null
}

/**
 * A new field in the middle of the page shown, upright to the reader whichever way the page is turned.
 * A signature field takes the first signer without one.
 */
export function newField(kind: PdfFieldKind, page: number, geometry: PageGeometry, fields: readonly PdfField[]): PdfField {
    const scale = scaleOf(geometry)
    const size = NEW_SIZE[kind]
    const box = keptOnCanvas(geometry, {
        left: (geometry.width - size.width * scale) / 2,
        top: (geometry.height - size.height * scale) / 2,
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
        role: signature ? freeSigner(fields) : null,
        fontFamily: null,
        fontStyle: FontStyle.REGULAR,
    }
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

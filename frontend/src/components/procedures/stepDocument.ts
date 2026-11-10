/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProcedureStepKind, StepDocument} from '@/api/generated/schema'

/**
 * The document a step asks to sign, as its page names it. `gone` marks one whose template was deleted or is
 * no longer the station's, which has no name to show.
 */
export type AskedDocument = StepDocument & {gone?: boolean}

/** What a step sends about the document it asks to sign. */
type StepKindFields = {kind: ProcedureStepKind; documentTemplateId: number | null}

/**
 * The document a step asks to sign, as its page names it, or null for a step ticked by hand. A document
 * step whose template is gone, or no longer the station's, comes back marked as gone.
 *
 * @param step      the step, by its kind and the document template it names
 * @param documents the document templates the page's steps ask to sign
 */
export function stepDocumentOf(
  step: {kind?: ProcedureStepKind; documentTemplateId?: number | null},
  documents: readonly StepDocument[],
): AskedDocument | null {
  if (step.kind !== 'DOCUMENT') return null
  const id = step.documentTemplateId
  return documents.find(document => document.id === id) ?? {id: id ?? 0, name: '', gone: true}
}

/** What a step sends about the document it asks to sign: the kind, and the template where it has one. */
export function stepKindOf(document: StepDocument | null): StepKindFields {
  return document ? {kind: 'DOCUMENT', documentTemplateId: document.id} : {kind: 'CHECK', documentTemplateId: null}
}

/**
 * Whether a step sends what it asks to sign: a step not stored yet always does, a stored one only where its
 * document was changed. A stored step whose document is gone or no longer suitable then saves its other
 * fields unchanged, rather than being refused or turned into a step ticked by hand.
 *
 * @param storedId the document the stored step asks to sign, null for one ticked by hand, undefined for a
 *                 step not stored yet
 * @param chosen   the document the step asks to sign now, null for one ticked by hand
 */
export function documentChanged(storedId: number | null | undefined, chosen: StepDocument | null): boolean {
  return storedId === undefined || storedId !== (chosen?.id ?? null)
}

/**
 * The fields a step's save carries about its document: its kind where {@link documentChanged} says it is
 * sent, nothing otherwise.
 *
 * @param storedId as for {@link documentChanged}
 * @param chosen   as for {@link documentChanged}
 */
export function stepKindChange(storedId: number | null | undefined, chosen: StepDocument | null): Partial<StepKindFields> {
  return documentChanged(storedId, chosen) ? stepKindOf(chosen) : {}
}

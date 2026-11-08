/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ProcedureStepKind, StepDocument} from '@/api/generated/schema'

/**
 * The document a step asks to sign, as its page names it, or null for a step ticked by hand. A document
 * step whose template is gone, or no longer the station's, comes back without a name.
 *
 * @param step      the step, by its kind and the document template it names
 * @param documents the document templates the page's steps ask to sign
 */
export function stepDocumentOf(
  step: {kind?: ProcedureStepKind; documentTemplateId?: number | null},
  documents: readonly StepDocument[],
): StepDocument | null {
  if (step.kind !== 'DOCUMENT') return null
  const id = step.documentTemplateId
  return documents.find(document => document.id === id) ?? {id: id ?? 0, name: ''}
}

/** What a step sends about the document it asks to sign: the kind, and the template where it has one. */
export function stepKindOf(document: StepDocument | null): {kind: ProcedureStepKind; documentTemplateId: number | null} {
  return document ? {kind: 'DOCUMENT', documentTemplateId: document.id} : {kind: 'CHECK', documentTemplateId: null}
}

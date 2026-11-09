/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {ProcedureItem, StepDocumentState} from '@/api/generated/schema'
import ProcedureStepDocument from './ProcedureStepDocument.vue'

const STEP: ProcedureItem = {
    id: 11, procedureId: 3, title: 'Antrag unterschreiben', description: null, note: null, isPublic: true,
    userAssigned: true, position: 0, checked: false, checkedAt: null, checkedBy: null, kind: 'DOCUMENT',
    documentTemplateId: 7, generationId: 900,
}

function state(progress: StepDocumentState['progress'], openFieldId: number | null = null): StepDocumentState {
    return {itemId: 11, documentId: 40, progress, settled: 1, fields: 2, openFieldId}
}

async function mountStep(props: Record<string, unknown>) {
    return mountSuspended(ProcedureStepDocument, {props: {item: STEP, dependencyMet: true, documentName: 'Aufnahmeantrag', ...props}})
}

/** A document step says where its document stands and offers the reader's ways on from there. */
describe('ProcedureStepDocument', () => {
    it('names the document and how many fields are done while it waits', async () => {
        const view = await mountStep({documentState: state('WAITING', 5), signHref: '/station/signing/5', signLabel: 'Jetzt unterschreiben'})

        expect(view.text()).toContain('Aufnahmeantrag')
        expect(view.find('[data-testid="procedure-step-document-state"]').text()).toContain('1')
        expect(view.find('[data-testid="procedure-step-document-sign"]').text()).toBe('Jetzt unterschreiben')
        expect(view.find('[data-testid="procedure-step-ask-again"]').exists()).toBe(false)
    })

    it('says a document not made yet is waiting for its turn, or missing once its turn came', async () => {
        const later = await mountStep({documentState: null, dependencyMet: false})
        const missing = await mountStep({documentState: null, dependencyMet: true})

        expect(later.find('[data-testid="procedure-step-document-state"]').text())
            .not.toBe(missing.find('[data-testid="procedure-step-document-state"]').text())
    })

    it('offers asking again and tells the page when pressed', async () => {
        const view = await mountStep({documentState: state('WITHDRAWN'), canAskAgain: true})

        await view.find('[data-testid="procedure-step-ask-again"]').trigger('click')

        expect(view.emitted('ask-again')).toHaveLength(1)
    })
})

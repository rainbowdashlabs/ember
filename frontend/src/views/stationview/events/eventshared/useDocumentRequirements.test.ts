/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {DocumentTemplateKind, type RequiredTemplate} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import {useDocumentRequirements, withoutTwice} from './useDocumentRequirements'

vi.mock('@/api', () => ({
    appointmentDocuments: {
        offeredTemplates: vi.fn(),
        listRequirements: vi.fn(),
        setRequirements: vi.fn(),
    },
}))

function template(templateId: number, name: string): RequiredTemplate {
    return {templateId, name, kind: DocumentTemplateKind.PDF, version: 1, archived: false}
}

const consent = template(1, 'Einverständnis')
const health = template(2, 'Gesundheitsbogen')

describe('the documents an appointment asks for', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.offeredTemplates).mockResolvedValue([consent, health])
        vi.mocked(appointmentDocuments.listRequirements).mockReset()
        vi.mocked(appointmentDocuments.setRequirements).mockReset()
    })

    it('reads what a saved appointment asks for, and nothing for a new one', async () => {
        vi.mocked(appointmentDocuments.listRequirements).mockResolvedValue([consent])
        const requirements = useDocumentRequirements()

        await requirements.load({kind: 'events', id: 5})
        expect(requirements.offered.value).toEqual([consent, health])
        expect(requirements.chosen.value).toEqual([consent])

        await requirements.load(null)
        expect(requirements.chosen.value).toEqual([])
    })

    it('takes the documents of an appointment template after the chosen ones, each once', async () => {
        vi.mocked(appointmentDocuments.listRequirements).mockResolvedValue([consent, health])
        const requirements = useDocumentRequirements()
        requirements.chosen.value = [health]

        await requirements.takeFrom({kind: 'event-templates', id: 7})

        expect(requirements.chosen.value.map(chosen => chosen.templateId)).toEqual([2, 1])
        expect(appointmentDocuments.listRequirements).toHaveBeenCalledWith({kind: 'event-templates', id: 7})
    })

    it('writes the chosen documents by their ids', async () => {
        vi.mocked(appointmentDocuments.setRequirements).mockResolvedValue([consent])
        const requirements = useDocumentRequirements()
        requirements.chosen.value = [consent]

        await requirements.save({kind: 'events', id: 5})

        expect(appointmentDocuments.setRequirements).toHaveBeenCalledWith({kind: 'events', id: 5}, [1])
    })

    it('keeps the first of a template named twice', () => {
        expect(withoutTwice([consent, health, consent])).toEqual([consent, health])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineComponent, ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {ProcedureDetail, ProcedureItem} from '@/api/generated/schema'
import {procedures} from '@/api'
import {useProcedureForm} from './useProcedureForm'

vi.mock('@/api', async (original) => {
    const actual = await original<typeof import('@/api')>()
    return {
        procedures: {
            ...actual.procedures,
            getTemplates: vi.fn(async () => []),
            getProcedure: vi.fn(),
            updateProcedure: vi.fn(async () => undefined),
            setParty: vi.fn(async () => undefined),
            setAbout: vi.fn(async () => undefined),
            setProcedureDependencies: vi.fn(async () => undefined),
            editItem: vi.fn(async () => undefined),
            addItem: vi.fn(),
            deleteItem: vi.fn(async () => undefined),
        },
        stationMembers: {listCompletions: vi.fn(async () => [])},
    }
})

vi.mock('@/components/documents/generation', () => ({
    audienceLists: async () => ({groups: [], tags: []}),
}))

let generatesDocuments = true

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => generatesDocuments}),
}))

const SIGN: ProcedureItem = {
    id: 11, procedureId: 3, title: 'Antrag unterschreiben', description: null, note: null, isPublic: true,
    userAssigned: true, position: 0, checked: false, checkedAt: null, checkedBy: null, kind: 'DOCUMENT',
    documentTemplateId: 7, generationId: null,
}

function detail(items: ProcedureItem[]): ProcedureDetail {
    return {
        procedure: {
            id: 3, stationId: '00000000-0000-4000-a000-000000000001', templateId: null, name: 'Aufnahme',
            description: null, isPublic: true, status: 'OPEN', dueAt: null, assignedBy: 1,
            createdAt: '2026-11-01T10:00:00Z', resolvedAt: null, eventId: null, eventDate: null, aboutMemberId: 5, resolvedBy: null,
        },
        items,
        dependencies: [],
        party: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
        named: [],
        about: null,
        documentStates: [],
        mayTick: true,
        readsAbout: false,
        documents: [{id: 7, name: 'Aufnahmeantrag'}],
    }
}

async function openForEditing(items: ProcedureItem[]) {
    vi.mocked(procedures.getProcedure).mockResolvedValue(detail(items))
    let form!: ReturnType<typeof useProcedureForm>
    await mountSuspended(defineComponent({
        setup() {
            form = useProcedureForm(ref(3), ref(null))
            return () => null
        },
    }))
    await form.reload()
    await flushPromises()
    return form
}

/** A procedure being edited sends what a step asks for only where the reader changed it. */
describe('useProcedureForm', () => {
    beforeEach(() => {
        vi.mocked(procedures.editItem).mockClear()
        vi.mocked(procedures.setAbout).mockClear()
        generatesDocuments = true
    })

    it('loads a document step with its document and leaves it alone on save', async () => {
        const form = await openForEditing([SIGN])

        expect(form.items.value[0]?.document).toEqual({id: 7, name: 'Aufnahmeantrag'})
        await form.submit()

        const sent = vi.mocked(procedures.editItem).mock.calls[0]?.[2]
        expect(sent).not.toHaveProperty('kind')
        expect(sent).not.toHaveProperty('documentTemplateId')
    })

    it('makes the procedure about another member only where the reader changed it', async () => {
        const form = await openForEditing([SIGN])
        expect(form.about.value).toBe('5')

        await form.submit()
        expect(procedures.setAbout).not.toHaveBeenCalled()

        form.about.value = '6'
        await form.submit()
        expect(procedures.setAbout).toHaveBeenCalledWith(3, 6, false)
        expect(form.aboutFixed.value).toBe(true)
    })

    it('holds back document steps on a procedure about nobody', async () => {
        const form = await openForEditing([SIGN])
        form.about.value = ''
        expect(form.documentsNeedAbout.value).toBe(false)

        form.addItem('Noch eins')
        form.updateItem(1, {...form.items.value[1]!, document: {id: 7, name: 'Aufnahmeantrag'}})
        expect(form.documentsNeedAbout.value).toBe(true)
    })

    it('sends a document step switched off as one ticked by hand', async () => {
        const form = await openForEditing([SIGN])

        form.updateItem(0, {...form.items.value[0]!, document: null})
        await form.submit()

        expect(vi.mocked(procedures.editItem).mock.calls[0]?.[2])
            .toMatchObject({kind: 'CHECK', documentTemplateId: null})
    })

    it('keeps a step that made its document from being switched', async () => {
        const form = await openForEditing([{...SIGN, generationId: 900}])

        expect(form.items.value[0]?.documentMade).toBe(true)
    })

    it('holds back a new document step its user may not ask for', async () => {
        generatesDocuments = false
        const form = await openForEditing([SIGN])
        expect(form.documentsNotYours.value).toBe(false)

        form.addItem('Noch eins')
        form.updateItem(1, {...form.items.value[1]!, document: {id: 7, name: 'Aufnahmeantrag'}})
        expect(form.documentsNotYours.value).toBe(true)
    })

    it('holds back a new step whose document is gone', async () => {
        const form = await openForEditing([{...SIGN, documentTemplateId: null}])
        expect(form.documentsGone.value).toBe(false)

        form.addItem('Noch eins')
        form.updateItem(1, {...form.items.value[1]!, document: {id: 0, name: ''}})
        expect(form.documentsGone.value).toBe(true)
    })
})

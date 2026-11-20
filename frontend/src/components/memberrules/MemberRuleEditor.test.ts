/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import MemberRuleEditor from './MemberRuleEditor.vue'
import {RuleNeed, RuleOperator, RuleSubject, type RuleNode} from '@/api/generated/schema'
import {emptyBlock, emptyCheck} from './ruleDraft'

const getRule = vi.fn()
const saveRule = vi.fn()
const removeRule = vi.fn()
const previewRule = vi.fn()
const listFields = vi.fn()

vi.mock('@/api', () => ({
    memberRules: {
        getRule: (...args: unknown[]) => getRule(...args),
        saveRule: (...args: unknown[]) => saveRule(...args),
        removeRule: (...args: unknown[]) => removeRule(...args),
        previewRule: (...args: unknown[]) => previewRule(...args),
        listFields: (...args: unknown[]) => listFields(...args),
        listOperators: async () => ({
            operators: [
                {subject: RuleSubject.GROUP, fieldType: null, operator: RuleOperator.IN, need: RuleNeed.NOTHING},
            ],
            maxDepth: 6,
            maxChecks: 50,
        }),
    },
    memberGroups: {listGroups: async () => [{id: 4, name: 'Team'}]},
    userTags: {listChoosableTags: async () => []},
}))

const session = vi.hoisted(() => ({readsMembers: false}))
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => session.readsMembers}),
}))

const RULE: RuleNode = {...emptyBlock(), children: [{...emptyCheck(), groupId: 4}]}

/**
 * Keeping a group by hand or by a rule: the builder appears for a rule, saving a first rule asks first,
 * removing one asks too, and the preview names who would join and leave.
 *
 * @vitest-environment happy-dom
 */
describe('MemberRuleEditor', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        session.readsMembers = false
        listFields.mockResolvedValue([])
    })

    /** Adds one condition asking about the group Team, which leaves a first rule ready to save. */
    async function addGroupCheck(wrapper: ReturnType<typeof editor>) {
        const add = wrapper.findAll('button').find(button => button.text().trim() === 'Bedingung')
        await add!.trigger('click')
        await wrapper.get('select[aria-label="Worauf"]').setValue(RuleSubject.GROUP)
        await wrapper.get('select[aria-label="Gruppe wählen"]').setValue('4')
        await flushPromises()
    }

    function editor(onChanged = vi.fn()) {
        return mount(MemberRuleEditor, {
            attachTo: document.body,
            props: {kind: 'group', ownerId: 4, onChanged},
        })
    }

    it('shows no builder for a group kept by hand, and asks before saving a first rule', async () => {
        getRule.mockResolvedValue(null)
        saveRule.mockResolvedValue(RULE)
        const onChanged = vi.fn()
        const wrapper = editor(onChanged)
        await flushPromises()

        expect(wrapper.text()).not.toContain('Regel speichern')
        expect(listFields).not.toHaveBeenCalled()

        await wrapper.find('select').setValue('rule')
        expect(wrapper.text()).toContain('Regel speichern')
        expect(wrapper.text()).not.toContain('Blöcke ohne Bedingung')
        await addGroupCheck(wrapper)
        expect(wrapper.text()).not.toContain('Blöcke ohne Bedingung')

        const save = wrapper.findAll('button').find(button => button.text().includes('Regel speichern'))
        await save!.trigger('click')
        expect(saveRule).not.toHaveBeenCalled()
        expect(document.body.textContent).toContain('Ab jetzt bestimmt die Regel die Mitglieder')
        wrapper.unmount()
    })

    it('closes the question once a first rule is saved', async () => {
        getRule.mockResolvedValue(null)
        saveRule.mockResolvedValue(RULE)
        const wrapper = editor()
        await flushPromises()
        await wrapper.find('select').setValue('rule')
        await addGroupCheck(wrapper)

        const save = wrapper.findAll('button').find(button => button.text().includes('Regel speichern'))
        await save!.trigger('click')
        document.body.querySelector<HTMLButtonElement>('[data-confirm]')!.click()
        await flushPromises()

        expect(saveRule).toHaveBeenCalledTimes(1)
        expect(document.body.textContent).not.toContain('Ab jetzt bestimmt die Regel die Mitglieder')
        wrapper.unmount()
    })

    it('saves a changed rule at once and tells the page', async () => {
        getRule.mockResolvedValue(RULE)
        saveRule.mockResolvedValue(RULE)
        const onChanged = vi.fn()
        const wrapper = editor(onChanged)
        await flushPromises()

        const save = wrapper.findAll('button').find(button => button.text().includes('Regel speichern'))
        await save!.trigger('click')
        await flushPromises()

        expect(saveRule).toHaveBeenCalledWith('group', 4, RULE)
        expect(onChanged).toHaveBeenCalled()
        wrapper.unmount()
    })

    it('names who would join and leave in the preview', async () => {
        getRule.mockResolvedValue(RULE)
        previewRule.mockResolvedValue({
            matching: [{id: 1, name: 'Anna'}],
            joining: [{id: 1, name: 'Anna'}],
            leaving: [{id: 2, name: 'Ben'}],
        })
        const wrapper = editor()
        await flushPromises()

        const refresh = wrapper.findAll('button').find(button => button.text().includes('Vorschau'))
        await refresh!.trigger('click')
        await flushPromises()

        expect(previewRule).toHaveBeenCalledWith('group', 4, RULE)
        expect(wrapper.text()).toContain('Anna')
        expect(wrapper.text()).toContain('Ben')
        wrapper.unmount()
    })

    it('shows a rule reading member data read only to a reader who may not read members', async () => {
        getRule.mockResolvedValue({
            ...emptyBlock(),
            children: [{...emptyCheck(), subject: RuleSubject.FIELD, operator: RuleOperator.IS_NO, fieldId: 3}],
        })
        const wrapper = editor()
        await flushPromises()

        expect(wrapper.text()).toContain('Liest Mitgliedsdaten')
        expect(wrapper.text()).not.toContain('gibt es nicht mehr')
        expect(wrapper.text()).toContain('Ändern kann sie nur, wer Mitglieder sehen darf')
        expect(wrapper.text()).not.toContain('Regel speichern')
        expect(wrapper.find('fieldset').attributes('disabled')).toBeDefined()
        wrapper.unmount()
    })

    it('says a rule whose field was deleted rests and keeps its members', async () => {
        session.readsMembers = true
        getRule.mockResolvedValue({
            ...emptyBlock(),
            children: [{...emptyCheck(), subject: RuleSubject.CLUSTER_FIELD, operator: RuleOperator.IS_YES}],
        })
        const wrapper = editor()
        await flushPromises()

        expect(wrapper.text()).toContain('Die Regel ruht und behält ihre Mitglieder')
        expect(wrapper.text()).not.toContain('Diese Bedingung ist noch nicht vollständig')
        wrapper.unmount()
    })

    it('says a field the reader may not see is there, rather than gone', async () => {
        session.readsMembers = true
        getRule.mockResolvedValue({
            ...emptyBlock(),
            children: [{...emptyCheck(), subject: RuleSubject.FIELD, operator: RuleOperator.IS_YES, fieldId: 7}],
        })
        const wrapper = editor()
        await flushPromises()

        expect(wrapper.text()).toContain('Dieses Feld darfst du nicht sehen')
        expect(wrapper.text()).not.toContain('Die Regel ruht')
        wrapper.unmount()
    })

    it('keeps saving closed while a check is incomplete', async () => {
        getRule.mockResolvedValue({...emptyBlock(), children: [emptyCheck()]})
        const wrapper = editor()
        await flushPromises()

        const save = wrapper.findAll('button').find(button => button.text().includes('Regel speichern'))
        expect(save!.attributes('disabled')).toBeDefined()
        expect(wrapper.text()).toContain('Noch nicht vollständige Bedingungen: 1')
        wrapper.unmount()
    })

    it('asks before removing a rule, which keeps the members', async () => {
        getRule.mockResolvedValue(RULE)
        const wrapper = editor()
        await flushPromises()

        await wrapper.find('select').setValue('hand')

        expect(removeRule).not.toHaveBeenCalled()
        expect(document.body.textContent).toContain('Die jetzigen Mitglieder bleiben')
        wrapper.unmount()
    })
})

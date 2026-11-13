/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import AddToEventGroupCard from './AddToEventGroupCard.vue'

const api = vi.hoisted(() => ({
  eventGroups: {
    listEventGroups: vi.fn(async () => [
      {id: 3, name: 'Grundlehrgang', cancelled: false},
      {id: 4, name: 'Abgesagt', cancelled: true},
    ]),
    getEventGroup: vi.fn(async () => ({sets: [{id: 31, name: 'Ausbildungsabende'}]})),
    addPart: vi.fn(async () => ({})),
  },
}))
vi.mock('@/api', () => api)

describe('AddToEventGroupCard', () => {
  it('offers the open event groups and adds the appointment into the chosen block', async () => {
    const onAdded = vi.fn()
    const wrapper = mount(AddToEventGroupCard, {props: {eventId: 12, onAdded}})
    await flushPromises()

    const options = wrapper.find('[data-testid="add-to-event-group-group"]').findAll('option')
    expect(options.map(option => option.text())).toEqual(['Terminreihe wählen', 'Grundlehrgang'])

    await wrapper.find('[data-testid="add-to-event-group-group"]').setValue('3')
    await flushPromises()
    await wrapper.find('[data-testid="add-to-event-group-save"]').trigger('click')
    await flushPromises()

    expect(api.eventGroups.addPart).toHaveBeenCalledWith(3, 12, 31)
    expect(onAdded).toHaveBeenCalled()
  })

  it('shows the refusal and does not reload when the appointment cannot join', async () => {
    api.eventGroups.addPart.mockRejectedValueOnce(new Error('refused'))
    const onAdded = vi.fn()
    const wrapper = mount(AddToEventGroupCard, {props: {eventId: 12, onAdded}})
    await flushPromises()

    await wrapper.find('[data-testid="add-to-event-group-group"]').setValue('3')
    await flushPromises()
    await wrapper.find('[data-testid="add-to-event-group-save"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(onAdded).not.toHaveBeenCalled()
  })

  it('drops the blocks of a group that is no longer chosen', async () => {
    let answerFirst: (value: {sets: {id: number, name: string}[]}) => void = () => undefined
    api.eventGroups.getEventGroup.mockImplementationOnce(() => new Promise(resolve => {
      answerFirst = resolve
    }))
    const wrapper = mount(AddToEventGroupCard, {props: {eventId: 12}})
    await flushPromises()

    const select = wrapper.find('[data-testid="add-to-event-group-group"]')
    await select.setValue('3')
    await select.setValue('')
    answerFirst({sets: [{id: 99, name: 'Veraltet'}]})
    await flushPromises()

    expect(wrapper.find('[data-testid="add-to-event-group-set"]').exists()).toBe(false)
  })

  it('links to a new event group that starts from this appointment', async () => {
    const wrapper = mount(AddToEventGroupCard, {props: {eventId: 12}})
    await flushPromises()

    const link = wrapper.findComponent({name: 'BaseButton'})
    expect(link.props('to')).toEqual({name: 'event-group-new', query: {appointment: 12}})
  })
})

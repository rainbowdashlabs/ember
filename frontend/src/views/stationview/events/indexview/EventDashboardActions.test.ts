/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import EventDashboardActions from './EventDashboardActions.vue'

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({useRouter: () => ({push})}))
const routes = vi.hoisted(() => ({current: {} as Record<string, string>}))
vi.mock('@/composables/useEventRoutes', () => ({useEventRoutes: () => routes.current}))

/** The station's dashboard starts a new event group; the association's, which has none, does not offer it. */
describe('EventDashboardActions', () => {
  beforeEach(() => {
    push.mockClear()
  })

  it('opens a new event group where the station has them', async () => {
    routes.current = {categories: 'event-categories', batch: 'event-batch', eventGroupCreate: 'event-group-new'}
    const wrapper = mount(EventDashboardActions)

    await wrapper.find('[data-testid="event-group-create"]').trigger('click')

    expect(push).toHaveBeenCalledWith({name: 'event-group-new'})
  })

  it('offers no event group where there are none', () => {
    routes.current = {categories: 'event-categories', batch: 'event-batch'}
    const wrapper = mount(EventDashboardActions)

    expect(wrapper.find('[data-testid="event-group-create"]').exists()).toBe(false)
  })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import AccountDetailsSection from './AccountDetailsSection.vue'
import type {OwnNameChange} from '@/api/generated/schema'

function mountSection(pendingName: OwnNameChange | null, withdrawName = vi.fn().mockResolvedValue(undefined)) {
  const wrapper = mount(AccountDetailsSection, {
    props: {
      firstName: 'Mara',
      lastName: 'Nager',
      email: 'mara@test.com',
      username: '',
      emailChangePending: false,
      pendingName,
      action: vi.fn().mockResolvedValue(undefined),
      withdrawName,
    },
  })
  return {wrapper, withdrawName}
}

describe('AccountDetailsSection', () => {
  it('says which new name waits and lets the member take it back', async () => {
    const {wrapper, withdrawName} = mountSection({firstName: 'Mia', lastName: 'Neu', requestedAt: '2026-10-01T10:00:00Z'})

    expect(wrapper.get('[data-testid="name-pending"]').text()).toContain('„Mia Neu“')
    await wrapper.get('[data-testid="withdraw-name"]').trigger('click')

    expect(withdrawName).toHaveBeenCalled()
  })

  it('says nothing about a name while none waits', () => {
    const {wrapper} = mountSection(null)

    expect(wrapper.find('[data-testid="name-pending"]').exists()).toBe(false)
  })
})

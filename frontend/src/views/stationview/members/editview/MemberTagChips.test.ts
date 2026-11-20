/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import MemberTagChips from './MemberTagChips.vue'
import deDE from '@/i18n/de-DE'
import {createTag} from '@/test/mocks/factories'

const api = vi.hoisted(() => ({
  userTags: {getTagMembers: vi.fn(), setTagMembers: vi.fn()},
}))
vi.mock('@/api', () => api)

function chips(ruled: number[]) {
  return mount(MemberTagChips, {
    props: {
      memberId: 7,
      tags: [createTag({id: 1, name: 'Presse'}), createTag({id: 2, name: 'Leitung'})],
      initialTagIds: new Set([2]),
      ruled: new Set(ruled),
    },
    global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})]},
  })
}

/** A member's tags on their edit page, where a tag a rule fills stays as the rule keeps it. */
describe('MemberTagChips', () => {
  it('shows a tag a rule fills as it is, but does not let it be toggled', async () => {
    const view = chips([2])
    const kept = view.findAll('button').find(button => button.text().startsWith('Leitung'))!

    expect(kept.attributes('disabled')).toBeDefined()
    expect(kept.attributes('aria-pressed')).toBe('true')
    expect(kept.attributes('title')).toBe(deDE.memberRules.keptByRuleHint)
    expect(kept.text()).toContain(deDE.memberRules.byRuleShort)
    await kept.trigger('click')
    expect(api.userTags.setTagMembers).not.toHaveBeenCalled()
  })

  it('lets a hand-kept tag be toggled', () => {
    const view = chips([2])
    const open = view.findAll('button').find(button => button.text() === 'Presse')!

    expect(open.attributes('disabled')).toBeUndefined()
    expect(open.attributes('title')).toBeUndefined()
  })
})

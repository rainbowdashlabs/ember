/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import NewsAuthorAvatar from './NewsAuthorAvatar.vue'
import {createIdentity} from '@/test/mocks/factories'

const shown = vi.hoisted(() => ({value: true}))

vi.mock('@/composables/useMemberCards', () => ({useMemberCardsShown: () => shown.value}))

function avatar() {
  return mount(NewsAuthorAvatar, {
    props: {author: createIdentity({name: 'Anna Muster'}), name: 'Anna Muster'},
    global: {
      stubs: {
        UserAvatar: true,
        MemberCardTrigger: {
          props: ['enabled'],
          template: '<div :data-enabled="enabled"><slot :toggle="() => {}" :trigger-attrs="{}"/></div>',
        },
      },
    },
  })
}

describe('the author of a news entry', () => {
  it('opens their member card', () => {
    shown.value = true

    expect(avatar().find('[data-testid="news-author-card"]').exists()).toBe(true)
  })

  it('is a plain picture where a screen turned cards off', () => {
    shown.value = false

    expect(avatar().find('[data-testid="news-author-card"]').exists()).toBe(false)
  })
})

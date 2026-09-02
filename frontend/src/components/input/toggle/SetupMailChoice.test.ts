/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import SetupMailChoice from './SetupMailChoice.vue'
import {sessionInfo} from '@/util/sessionState'

const CHOICE = '[data-testid="setup-mail-choice"]'
const IMPOSSIBLE = '[data-testid="setup-mail-impossible"]'

function choice(props: {modelValue?: boolean; hasAddress?: boolean} = {}) {
  return mount(SetupMailChoice, {props})
}

afterEach(() => {
  sessionInfo.value = null
})

describe('SetupMailChoice', () => {
  it('offers the choice where the instance can send', () => {
    sessionInfo.value = {canSendMail: true}

    expect(choice().find(CHOICE).exists()).toBe(true)
  })

  it('offers the choice where the instance says nothing about sending', () => {
    sessionInfo.value = {}

    expect(choice().find(CHOICE).exists()).toBe(true)
  })

  it('sends at once until somebody says otherwise', () => {
    sessionInfo.value = {canSendMail: true}

    expect(choice().get(CHOICE).get('[role="switch"]').attributes('aria-checked')).toBe('true')
  })

  it('says what holding the mail back means', async () => {
    sessionInfo.value = {canSendMail: true}
    const wrapper = choice({modelValue: false})

    expect(wrapper.get(CHOICE).text()).toContain('Mitgliederliste')
  })

  it('asks nothing where the instance has nowhere to send through', () => {
    sessionInfo.value = {canSendMail: false}
    const wrapper = choice()

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.get(IMPOSSIBLE).text()).toContain('Mailserver')
  })

  it('asks nothing about somebody who has no address of their own', () => {
    sessionInfo.value = {canSendMail: true}
    const wrapper = choice({hasAddress: false})

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.find(IMPOSSIBLE).exists()).toBe(false)
  })

  it('stays silent for somebody with no address on an instance that cannot send either', () => {
    sessionInfo.value = {canSendMail: false}
    const wrapper = choice({hasAddress: false})

    expect(wrapper.find(CHOICE).exists()).toBe(false)
    expect(wrapper.find(IMPOSSIBLE).exists()).toBe(false)
  })

  it('carries the answer back out', async () => {
    sessionInfo.value = {canSendMail: true}
    const wrapper = choice()

    await wrapper.get(CHOICE).get('[role="switch"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
})

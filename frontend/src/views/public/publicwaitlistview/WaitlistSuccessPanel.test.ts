/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import WaitlistSuccessPanel from './WaitlistSuccessPanel.vue'
import deDE from '@/i18n/de-DE'

function panel(confirmedByMail: boolean) {
  return mount(WaitlistSuccessPanel, {
    props: {confirmedByMail},
    global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE}})]},
  })
}

describe('WaitlistSuccessPanel', () => {
  it('sends the reader to their inbox where a link is on its way', () => {
    expect(panel(true).text()).toContain(deDE.waitingList.publicRegistration.successText)
  })

  it('says the registration is in where nothing is sent', () => {
    const text = panel(false).text()

    expect(text).toContain(deDE.waitingList.publicRegistration.receivedTitle)
    expect(text).not.toContain(deDE.waitingList.publicRegistration.successText)
  })
})

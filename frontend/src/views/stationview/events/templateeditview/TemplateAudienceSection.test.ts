/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RestrictionMode} from '@/api/generated/schema'
import {emptyRestriction} from '@/components/input/restriction'
import TemplateAudienceSection from './TemplateAudienceSection.vue'

async function mountSection(restriction = emptyRestriction()) {
  return mountSuspended(TemplateAudienceSection, {
    props: {groups: [{id: 4, name: 'Jugend', color: null, groupSetId: null, position: 0, stationId: 's', userTypes: []}], tags: [], restriction, viewRestriction: emptyRestriction()},
  })
}

/** The template picks who may sign up and who sees the appointment the way the appointment form does. */
describe('TemplateAudienceSection', () => {
  it('starts open to everybody with a button for each restriction', async () => {
    const section = await mountSection()

    expect(section.find('[data-testid="restrict-registration"]').exists()).toBe(true)
    expect(section.find('[data-testid="restrict-visibility"]').exists()).toBe(true)
    expect(section.find('[data-testid="restriction-registration"]').exists()).toBe(false)
  })

  it('shows a restriction the template already has, with the template hint', async () => {
    const section = await mountSection({groupIds: [4], memberIds: [], tagIds: [], userTypes: [], mode: RestrictionMode.OR})

    expect(section.find('[data-testid="restriction-registration"]').exists()).toBe(true)
    expect(section.text()).toContain('Wer sich für Termine aus dieser Vorlage anmelden darf')
    expect(section.find('[data-testid="restrict-registration"]').exists()).toBe(false)
  })

  it('adds a restriction with its button', async () => {
    const section = await mountSection()

    await section.get('[data-testid="restrict-visibility"]').trigger('click')

    expect(section.find('[data-testid="restriction-visibility"]').exists()).toBe(true)
  })
})

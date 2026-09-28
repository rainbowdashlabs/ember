/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {FieldTypes} from '@/api/profileFields'
import {CLUSTER_FIELD_TYPES} from '@/api/clusterFields'
import {expirySettingsOf} from '@/util/expiry'
import {fieldTemplates} from './fieldTemplates'

/**
 * The settings a profile field may carry, as the server's record for them names them.
 *
 * <p>The server refuses a request whose settings name anything else, so a template that strays
 * outside this list cannot be applied at all.
 */
const SERVER_CONFIG_KEYS = [
  'description',
  'notifyOnChange',
  'overview',
  'options',
  'defaultValue',
  'computed',
  'sourceField',
  'sourceFieldId',
  'ageMode',
  'showAge',
  'warnFromDays',
  'reminderDays',
  'repeatEveryDays',
  'remindMember',
  'remindManagement',
]

describe('fieldTemplates', () => {
  const fields = fieldTemplates.flatMap(template =>
    template.fields.map(field => ({template: template.name, ...field})))

  it.each(fields)('$template: $name carries only settings the server names', field => {
    const unknown = Object.keys(field.config).filter(key => !SERVER_CONFIG_KEYS.includes(key))
    expect(unknown).toEqual([])
  })

  /** An association may ask for expiry dates, so the templates holding one are offered to it as well. */
  it('offers the templates with an expiry date to an association', () => {
    const offered = fieldTemplates
      .filter(template => template.fields.every(field =>
        (CLUSTER_FIELD_TYPES as readonly string[]).includes(field.fieldType)))
      .map(template => template.name)

    expect(offered).toEqual(expect.arrayContaining(['Führerschein', 'JuLeiCa', 'Erste Hilfe Kurs']))
    expect(offered).not.toContain('Geburtsdatum')
  })

  /** A date that runs out warns as early as renewing it takes; a date that records an event does not run out. */
  it('makes the dates that run out expiry dates', () => {
    const expiring = Object.fromEntries(fields
      .filter(field => field.fieldType === FieldTypes.EXPIRY_DATE)
      .map(field => [field.name, expirySettingsOf(field.config).warnFromDays]))

    expect(expiring).toEqual({
      'Führerschein gültig bis': 60,
      'JuLeiCa Ablaufdatum': 90,
      'Erste Hilfe gültig bis': 90,
    })
  })
})

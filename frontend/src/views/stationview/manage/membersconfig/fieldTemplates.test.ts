/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
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
]

describe('fieldTemplates', () => {
  const fields = fieldTemplates.flatMap(template =>
    template.fields.map(field => ({template: template.name, ...field})))

  it.each(fields)('$template: $name carries only settings the server names', field => {
    const unknown = Object.keys(field.config).filter(key => !SERVER_CONFIG_KEYS.includes(key))
    expect(unknown).toEqual([])
  })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {sameAudience, templateAudience} from './templateAudience'
import type {TemplateDetail} from '@/api/generated/schema'

/**
 * Whom a template enters, and whether a sheet was told exactly that. A sheet told exactly its
 * template's audience is started without one of its own, so it keeps following the template.
 */
const template: TemplateDetail = {
    id: 3,
    stationId: 's',
    name: 'Dienstabend',
    fields: [],
    groups: [{groupId: 8, position: 1}, {groupId: 5, position: 0}],
    userTypes: ['TEAM', 'MANAGER'],
}

describe('templateAudience', () => {
    it('takes the user types and the groups in the template\'s order', () => {
        expect(templateAudience(template)).toEqual({userTypes: ['TEAM', 'MANAGER'], groupIds: [5, 8]})
    })

    it('reads a template that names nothing as naming nobody', () => {
        expect(templateAudience({id: 1, stationId: 's', name: 'Leer', fields: [], groups: [], userTypes: []}))
            .toEqual({userTypes: [], groupIds: []})
    })
})

describe('sameAudience', () => {
    it('matches the template whatever order the types were ticked in', () => {
        expect(sameAudience({userTypes: ['MANAGER', 'TEAM'], groupIds: [5, 8]}, template)).toBe(true)
    })

    it('tells a changed type apart', () => {
        expect(sameAudience({userTypes: ['TEAM'], groupIds: [5, 8]}, template)).toBe(false)
    })

    it('tells a changed group or a changed order of groups apart', () => {
        expect(sameAudience({userTypes: ['TEAM', 'MANAGER'], groupIds: [5]}, template)).toBe(false)
        expect(sameAudience({userTypes: ['TEAM', 'MANAGER'], groupIds: [8, 5]}, template)).toBe(false)
    })
})

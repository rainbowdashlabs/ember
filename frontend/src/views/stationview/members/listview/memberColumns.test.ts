/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {ProfileField} from '@/api/generated/schema'
import {memberColumns} from './memberColumns'

function field(id: number, fieldType: string): ProfileField {
    return {id, name: `Frage ${id}`, fieldType, config: {}} as unknown as ProfileField
}

const sources = {
    t: (key: string) => key,
    groupsOf: () => [],
    tagsOf: () => [],
    answerOf: () => null,
    stationLocalColumns: false,
}

describe('memberColumns', () => {
    it('makes a column of every question that takes an answer and none of headings or spacers', () => {
        const keys = memberColumns([field(1, 'TEXT'), field(2, 'SECTION'), field(3, 'SPACER'), field(4, 'AGE')], sources)
            .map(column => column.key)

        expect(keys).toEqual(['name', 'userType', 'email', '1', '4'])
    })
})

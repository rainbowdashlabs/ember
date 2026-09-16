/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ageSourceOf, FieldTypes, type ProfileField} from './profileFields'

/**
 * Which question a calculated age reads.
 *
 * <p>The point of the identifier is that it survives what the question is called, and the point of
 * the name is that a field configured before there were identifiers still finds its source.
 */
describe('ageSourceOf', () => {
    const birthDate: ProfileField = {id: 10, name: 'Geburtsdatum', fieldType: FieldTypes.BIRTH_DATE}
    const joined: ProfileField = {id: 11, name: 'Beitrittsdatum', fieldType: FieldTypes.DATE}
    const fields = [birthDate, joined]

    it('reads the question the identifier names', () => {
        expect(ageSourceOf({sourceFieldId: 10}, fields)).toBe(birthDate)
        expect(ageSourceOf({sourceFieldId: 11}, fields)).toBe(joined)
    })

    /** The whole reason for the identifier: the question is still the question after a rename. */
    it('follows a renamed question', () => {
        const renamed = [{...birthDate, name: 'Geburtstag'}, joined]

        expect(ageSourceOf({sourceFieldId: 10, sourceField: 'Geburtsdatum'}, renamed)?.id).toBe(10)
    })

    /** Written before identifiers were recorded, and still worth reading. */
    it('falls back to the name where no identifier stands', () => {
        expect(ageSourceOf({sourceField: 'Geburtsdatum'}, fields)).toBe(birthDate)
    })

    it('finds nothing where the question is gone or was never named', () => {
        expect(ageSourceOf({sourceFieldId: 99}, fields)).toBeUndefined()
        expect(ageSourceOf({sourceField: 'Was anderes'}, fields)).toBeUndefined()
        expect(ageSourceOf({}, fields)).toBeUndefined()
    })
})

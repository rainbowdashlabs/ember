/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {templateTarget} from './templateTarget'

const station = {editRoute: 'documents-template-edit', useRoute: 'documents-template-use'}
const association = {editRoute: 'cluster-document-template-edit', useRoute: null}

describe('templateTarget', () => {
    it('opens the editor of a template the list owner keeps', () => {
        expect(templateTarget({id: 3, ofAssociation: false}, station))
            .toEqual({name: 'documents-template-edit', params: {id: 3}})
    })

    it('opens how the station uses a template of its association, which it does not change', () => {
        expect(templateTarget({id: 4, ofAssociation: true}, station))
            .toEqual({name: 'documents-template-use', params: {id: 4}})
    })

    it('opens the editor of the association for its own templates', () => {
        expect(templateTarget({id: 5, ofAssociation: true}, association))
            .toEqual({name: 'cluster-document-template-edit', params: {id: 5}})
    })
})

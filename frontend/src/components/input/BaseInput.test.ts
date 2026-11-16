/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import BaseInput from './BaseInput.vue'

/**
 * A field takes the whole row unless it is given a width of its own. With both on the element the
 * stylesheet's order decided, and a narrow time field took the row from the date beside it.
 */
describe('BaseInput width', () => {
    it('takes the whole row by default', () => {
        expect(mount(BaseInput, {props: {type: 'date'}}).find('input').classes()).toContain('w-full')
    })

    it('keeps the width it is given', () => {
        const classes = mount(BaseInput, {props: {type: 'time'}, attrs: {class: 'w-20 text-xs'}}).find('input').classes()

        expect(classes).toContain('w-20')
        expect(classes).not.toContain('w-full')
    })

    it('keeps a width given for a screen size only', () => {
        const classes = mount(BaseInput, {attrs: {class: ['sm:w-40', {'opacity-60': true}]}}).find('input').classes()

        expect(classes).not.toContain('w-full')
    })

    it('still takes the row when only a minimum or maximum is given', () => {
        expect(mount(BaseInput, {attrs: {class: 'min-w-0 max-w-xs'}}).find('input').classes()).toContain('w-full')
    })
})

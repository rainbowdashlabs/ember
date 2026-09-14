/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import ButtonRow from './ButtonRow.vue'
import PrimaryButton from './PrimaryButton.vue'

function mountRow(props: Record<string, unknown> = {}) {
    return mount(ButtonRow, {
        props,
        slots: {default: '<button>Eins</button><button>Zwei</button>'},
    })
}

function classesOf(wrapper: ReturnType<typeof mountRow>): string[] {
    return wrapper.element.className.split(/\s+/)
}

describe('ButtonRow', () => {
    it('stacks into one full width column on a phone', () => {
        expect(classesOf(mountRow())).toContain('grid-cols-1')
    })

    it('puts two to a line where the caller says the labels are short', () => {
        expect(classesOf(mountRow({pair: true}))).toContain('grid-cols-2')
    })

    it('is the plain row again from the breakpoint up', () => {
        const classes = classesOf(mountRow())

        expect(classes).toContain('sm:flex')
        expect(classes).toContain('sm:flex-wrap')
    })

    it('sits where it is told once it is a row again', () => {
        expect(classesOf(mountRow())).toContain('sm:justify-start')
        expect(classesOf(mountRow({align: 'center'}))).toContain('sm:justify-center')
        expect(classesOf(mountRow({align: 'end'}))).toContain('sm:justify-end')
        expect(classesOf(mountRow({align: 'between'}))).toContain('sm:justify-between')
    })

    it('draws everything it is given', () => {
        expect(mountRow().text()).toBe('EinsZwei')
    })

    /**
     * A grid column is wider than the words in it, so without this the label sits against the left
     * edge of a full width button while every other button centres its own.
     */
    it('centres a stretched button on a phone', () => {
        expect(classesOf(mountRow())).toContain('max-sm:[&>*]:justify-center')
    })

    it('never lets a button break its own label', () => {
        const button = mount(PrimaryButton, {slots: {default: 'Als ehemalig markieren'}})

        expect(button.element.className.split(/\s+/)).toContain('whitespace-nowrap')
    })
})

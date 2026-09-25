/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {pressedAControl} from './rowPress'

/**
 * Telling a press on the row from a press on something sitting in it.
 *
 * @vitest-environment happy-dom
 *
 * <p>Two callers depend on the same answer from opposite sides: a row that is a link must not follow
 * itself when a button in it was pressed, and a row that is not a link must not act on one either.
 */
describe('pressedAControl', () => {
    function press(row: string, aim: string): MouseEvent {
        document.body.innerHTML = `<div id="row">${row}</div>`
        const event = new MouseEvent('click', {bubbles: true, cancelable: true})
        document.querySelector(aim)!.dispatchEvent(event)
        return event
    }

    it('answers for a press on a button in the row', () => {
        expect(pressedAControl(press('<span>Test</span><button id="aim">Löschen</button>', '#aim'))).toBe(true)
    })

    it('answers for a press on an icon inside a button, which is what a finger actually hits', () => {
        expect(pressedAControl(press('<button><svg id="aim"/></button>', '#aim'))).toBe(true)
    })

    it('answers for a press on anything else that takes input', () => {
        expect(pressedAControl(press('<input id="aim"/>', '#aim'))).toBe(true)
        expect(pressedAControl(press('<select id="aim"></select>', '#aim'))).toBe(true)
        expect(pressedAControl(press('<div role="button" id="aim"></div>', '#aim'))).toBe(true)
    })

    it('leaves a press on the row itself to the row', () => {
        expect(pressedAControl(press('<span id="aim">Test</span><button>Löschen</button>', '#aim'))).toBe(false)
    })
})

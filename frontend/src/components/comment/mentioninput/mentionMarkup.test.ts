/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {MemberCompletion} from '@/api/generated/schema'
import {htmlToRaw, rawToHtml} from './mentionMarkup'

const MEMBER_UID = '00000000-0000-0000-0000-000000000007'

function member(name: string): MemberCompletion {
    return {
        id: 7,
        memberUid: MEMBER_UID,
        name,
        displayTag: null,
        nameColor: null,
        stationName: null,
        stationUid: '00000000-0000-0000-0000-000000000003',
    }
}

function rendered(raw: string, members: MemberCompletion[] = []): HTMLElement {
    const host = document.createElement('div')
    host.innerHTML = rawToHtml(raw, members)
    return host
}

describe('rawToHtml', () => {
    it('shows a member name chosen as markup as text', () => {
        const host = rendered(`@[station/${MEMBER_UID}:Mara]`, [member('<img src=x onerror=alert(1)>')])
        expect(host.querySelector('img')).toBeNull()
        expect(host.textContent).toBe('@<img src=x onerror=alert(1)>')
    })

    it('keeps a quote in the markup inside its attribute', () => {
        const host = rendered('@[GROUP:a" onmouseover="alert(1):3]')
        const chip = host.querySelector('span')
        expect(chip?.getAttribute('onmouseover')).toBeNull()
        expect(chip?.dataset.mentionName).toBe('a" onmouseover="alert(1)')
    })

    it('reads back what it wrote', () => {
        const raw = `Hallo @[station/${MEMBER_UID}:Mara & "Jo"] und @[GROUP:Team <A>:3]`
        expect(htmlToRaw(rendered(raw, [member('Mara')]))).toBe(raw)
    })
})

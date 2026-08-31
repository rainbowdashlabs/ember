/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent, nextTick} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {useOnboardingGuide} from './useOnboardingGuide'
import {activeStep, activeTaskKey, clearActiveTask, guideDismissed} from '@/util/onboardingState'

vi.mock('vue-router', () => ({useRoute: () => ({name: 'profile'})}))

/**
 * The navigation as the layout builds it: in the page at every width, and merely pushed off the side
 * when the drawer is shut. That is the shape the walk got wrong, so the test reproduces it rather
 * than removing the element.
 */
function drawer(open: boolean) {
    const aside = document.createElement('aside')
    const link = document.createElement('a')
    link.setAttribute('data-onboarding', 'nav.profile')
    aside.appendChild(link)
    document.body.appendChild(aside)
    const left = open ? 0 : -256
    link.getBoundingClientRect = () => new DOMRect(left, 100, 256, 40)
    return link
}

function menuButton() {
    const button = document.createElement('button')
    button.setAttribute('data-onboarding', 'nav.open')
    document.body.appendChild(button)
    button.getBoundingClientRect = () => new DOMRect(8, 8, 40, 40)
    return button
}

function guideOn() {
    let api: ReturnType<typeof useOnboardingGuide> | null = null
    mount(defineComponent({
        setup() {
            api = useOnboardingGuide()
            return () => null
        },
    }))
    return api!
}

beforeEach(() => {
    document.body.innerHTML = ''
    window.innerWidth = 390
    guideDismissed.value = false
    activeTaskKey.value = 'member.profile'
    activeStep.value = 0
})

afterEach(() => {
    clearActiveTask()
    document.body.innerHTML = ''
})

/** A form as the profile draws it: one field the reader may write in, one the station fills. */
function profileForm(readerFieldValue: string, stationFieldValue: string) {
    const form = document.createElement('div')
    form.setAttribute('data-onboarding', 'profile.fields')
    const own = document.createElement('input')
    own.value = readerFieldValue
    const stations = document.createElement('input')
    stations.readOnly = true
    stations.value = stationFieldValue
    form.append(own, stations)
    document.body.appendChild(form)
    form.getBoundingClientRect = () => new DOMRect(0, 100, 400, 200)
    return {form, own, stations}
}

describe('a step that is read rather than done', () => {
    beforeEach(() => {
        activeStep.value = 1
    })

    it('is not carried on by a field the reader cannot write in', async () => {
        const {form, stations} = profileForm('', 'Aus der Wache')
        const guide = guideOn()
        await nextTick()
        expect(guide.step.value?.advance).toBe('read')

        stations.dispatchEvent(new FocusEvent('focusout', {bubbles: true}))
        await nextTick()

        expect(activeStep.value).toBe(1)
        expect(form.isConnected).toBe(true)
    })

    it('is carried on by the reader filling in what was missing', async () => {
        const {own} = profileForm('Etwas Eingetragenes', '')
        guideOn()
        await nextTick()

        own.dispatchEvent(new FocusEvent('focusout', {bubbles: true}))
        await nextTick()

        expect(activeStep.value).toBe(2)
    })
})

describe('the walk on a width where the navigation slides away', () => {
    it('rings the menu instead of a place beyond the edge of the window', async () => {
        drawer(false)
        const menu = menuButton()
        const guide = guideOn()
        await nextTick()

        expect(guide.behindMenu.value).toBe(true)
        expect(guide.box.value).toEqual({
            top: menu.getBoundingClientRect().top,
            left: menu.getBoundingClientRect().left,
            width: 40,
            height: 40,
        })
    })

    it('picks the target up again once the drawer has come in', async () => {
        const link = drawer(false)
        menuButton()
        const guide = guideOn()
        await nextTick()
        expect(guide.behindMenu.value).toBe(true)

        link.getBoundingClientRect = () => new DOMRect(0, 100, 256, 40)
        document.dispatchEvent(new Event('transitionend', {bubbles: true}))
        await new Promise(resolve => requestAnimationFrame(() => resolve(null)))

        expect(guide.behindMenu.value).toBe(false)
        expect(guide.box.value?.left).toBe(0)
    })

    it('says nothing rather than ringing the edge when there is no menu to send anybody to', async () => {
        drawer(false)
        const guide = guideOn()
        await nextTick()

        expect(guide.behindMenu.value).toBe(false)
        expect(guide.box.value).toBeNull()
    })

    it('leaves a navigation that stands open alone', async () => {
        drawer(true)
        menuButton()
        const guide = guideOn()
        await nextTick()

        expect(guide.behindMenu.value).toBe(false)
        expect(guide.box.value?.left).toBe(0)
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {flowFor, type OnboardingStep} from '@/util/onboardingFlows'
import {activeStep, activeTaskKey, guideDismissed} from '@/util/onboardingState'

export interface TargetBox {
    top: number
    left: number
    width: number
    height: number
}

const ATTRIBUTE = 'data-onboarding'

/** The control that pulls the navigation in, on the widths where it is not standing open. */
const MENU_MARK = 'nav.open'

/**
 * How long a step's target is given to appear before an optional step is passed over, when nothing
 * else settles the question. Long enough for a page to fetch what it shows, short enough that a
 * reader waiting on a step that will never light up is not left wondering.
 */
const SETTLE_MS = 1500

function findTarget(mark: string | undefined): HTMLElement | null {
    if (!mark) return null
    return document.querySelector<HTMLElement>(`[${ATTRIBUTE}="${CSS.escape(mark)}"]`)
}

/**
 * Whether anything has been put into the marked control, or into any of the fields it holds. A mark
 * sits on a single field in one flow and on a whole form in another, and both have to answer this.
 */
function filled(element: HTMLElement): boolean {
    const controls = element.matches('input, textarea, select')
        ? [element as HTMLInputElement]
        : Array.from(element.querySelectorAll<HTMLInputElement>('input, textarea, select'))
    // A field the reader cannot write in does not answer for them. Some of what a profile holds is
    // the station's to fill and readable only, and counting it would carry the step on the moment
    // somebody tabbed past it, crediting them with an answer that was already there.
    return controls.some(control => !control.readOnly && !control.disabled && control.value.trim() !== '')
}

/**
 * Whether the thing being pointed at cannot be used yet, which a step must not demand a click on.
 * The training's start button is the case that matters: it stays disabled until a catalogue is
 * ticked, so a reader told to press it presses nothing.
 */
function isBlocked(element: HTMLElement): boolean {
    if (element.matches(':disabled') || element.getAttribute('aria-disabled') === 'true') return true
    return element.querySelector(':scope > :disabled') !== null
}

/**
 * What to point at when the target itself is not on the page but the thing that holds it is.
 *
 * An entry of a menu that is folded away is nowhere in the page, and saying "this is under
 * Notifications, shall I take you there" is a poorer answer than pointing at the menu it hides in.
 * The marks are written as a path, so the holder is the mark one segment shorter.
 */
function findHolder(mark: string | undefined): HTMLElement | null {
    if (!mark) return null
    let path = mark
    while (path.includes('.')) {
        path = path.slice(0, path.lastIndexOf('.'))
        const holder = findTarget(path)
        if (holder) return holder
    }
    return null
}

/**
 * Whether the element sits outside the window sideways, which is what a navigation drawer does to
 * everything inside it on the widths where it slides away.
 *
 * Only sideways counts. Something below the fold is reachable by scrolling and the walk scrolls to
 * it, but a drawer standing at minus its own width is not reachable by any amount of scrolling: it
 * is reachable by opening the drawer, which is a different thing to ask for. Zero size counts too,
 * because a drawer that is folded rather than slid away leaves its contents measuring nothing.
 */
function outOfReach(element: HTMLElement): boolean {
    if (typeof window === 'undefined') return false
    const rect = element.getBoundingClientRect()
    if (rect.width === 0 && rect.height === 0) return true
    return rect.right <= 0 || rect.left >= window.innerWidth
}

function prefersReducedMotion(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Finds what the current step points at, keeps the ring on it, and carries the flow forward as the
 * reader works.
 *
 * The page stays fully usable throughout. A step whose element is nowhere to be seen falls back to
 * naming its page rather than pointing into thin air, and wandering off mid-flow costs nothing: the
 * step simply waits.
 */
export function useOnboardingGuide() {
    const route = useRoute()
    const box = ref<TargetBox | null>(null)
    const reducedMotion = ref(false)
    /** Set while the ring sits on what holds the target rather than on the target itself. */
    const revealing = ref(false)
    /** Set while the target is there but cannot be used yet. */
    const blocked = ref(false)
    /**
     * Set while the ring sits on the menu button because the target is inside a drawer that is not
     * standing open. It is its own state rather than a kind of {@link revealing}: one asks the
     * reader to unfold something they can see, the other to summon something they cannot.
     */
    const behindMenu = ref(false)

    const steps = computed<OnboardingStep[]>(() => (activeTaskKey.value ? flowFor(activeTaskKey.value) : []))
    const step = computed<OnboardingStep | null>(() => steps.value[activeStep.value] ?? null)
    const onStepRoute = computed(() => !step.value?.route || route.name === step.value.route)
    const finished = computed(() => steps.value.length > 0 && activeStep.value >= steps.value.length)

    /** Where the reader should look: at the element, or at the page the element is on. */
    const pointing = computed(() => box.value !== null)

    /**
     * Whether the ring sits in the lower half of the window, which is where the bubble would cover
     * it. The bubble moves to the opposite half rather than over the very thing being pointed at.
     */
    const targetLow = computed(() => {
        if (!box.value || typeof window === 'undefined') return false
        return box.value.top + box.value.height / 2 > window.innerHeight / 2
    })

    const gaze = computed<'left' | 'mid' | 'right'>(() => {
        if (!box.value || typeof window === 'undefined') return 'mid'
        const centre = box.value.left + box.value.width / 2
        const third = window.innerWidth / 3
        if (centre < third) return 'left'
        if (centre > third * 2) return 'right'
        return 'mid'
    })

    let observer: MutationObserver | null = null
    let scrolledFor = -1
    let settleTimer: ReturnType<typeof setTimeout> | null = null
    let measureQueued = false

    function stopSettling() {
        if (settleTimer !== null) clearTimeout(settleTimer)
        settleTimer = null
    }

    /**
     * Whether an optional step whose target is nowhere may be passed over.
     *
     * Absence alone does not settle it. A page that has not finished loading looks exactly like a
     * page the element is not on, and passing over the step then walks the flow past the one thing
     * it had to point at, with nothing left to point at afterwards: that is how the calendar task
     * lost its ring for anybody who had no feed token yet.
     *
     * What settles it is the step that follows. Seeing that element proves the page is drawn and the
     * optional one is genuinely not needed, so the walk moves on at once. When nothing follows, or
     * when neither is there yet, a short wait stands in and the observer picks up whatever appears
     * in the meantime.
     */
    function considerSkipping() {
        const next = steps.value[activeStep.value + 1]
        if (next && findTarget(next.target)) {
            advance()
            return
        }
        if (settleTimer !== null) return
        const skippingStep = activeStep.value
        settleTimer = setTimeout(() => {
            settleTimer = null
            if (skippingStep !== activeStep.value) return
            if (!step.value?.optional || !onStepRoute.value) return
            if (findTarget(step.value.target)) return
            advance()
        }, SETTLE_MS)
    }

    function measure() {
        if (!activeTaskKey.value) {
            box.value = null
            revealing.value = false
            blocked.value = false
            behindMenu.value = false
            return
        }
        const own = findTarget(step.value?.target)
        const element = own ?? findHolder(step.value?.target)
        revealing.value = own === null && element !== null
        if (!element) {
            box.value = null
            blocked.value = false
            behindMenu.value = false
            if (step.value?.optional && onStepRoute.value) considerSkipping()
            return
        }
        stopSettling()

        // The navigation is in the page at every width; on a narrow one it is merely pushed off the
        // side. So the target is found, and ringing where it says it is draws the ring past the edge
        // of the window, which is the whole of what a reader on a phone saw: nothing. Send them to
        // the menu first, and pick the target up again once it has come in.
        if (outOfReach(element)) {
            const opener = findTarget(MENU_MARK)
            if (opener && !outOfReach(opener)) {
                behindMenu.value = true
                revealing.value = false
                blocked.value = false
                const openerRect = opener.getBoundingClientRect()
                box.value = {
                    top: openerRect.top,
                    left: openerRect.left,
                    width: openerRect.width,
                    height: openerRect.height,
                }
                return
            }
            // Nothing to send them to, so say nothing rather than ring the edge of the window.
            behindMenu.value = false
            box.value = null
            blocked.value = false
            return
        }
        behindMenu.value = false

        blocked.value = own !== null && isBlocked(own)
        const rect = element.getBoundingClientRect()
        box.value = {top: rect.top, left: rect.left, width: rect.width, height: rect.height}
        if (scrolledFor !== activeStep.value) {
            scrolledFor = activeStep.value
            element.scrollIntoView({block: 'center', behavior: reducedMotion.value ? 'auto' : 'smooth'})
        }
    }

    /**
     * One measurement per frame, however many mutations ask for it.
     *
     * Watching class changes across the page means every hover asks, and measuring on each would be
     * work nobody sees. Folding them into the next frame keeps the cost of the wider watch to what
     * the narrower one used to cost.
     */
    function scheduleMeasure() {
        if (measureQueued) return
        measureQueued = true
        const run = () => {
            measureQueued = false
            measure()
        }
        if (typeof requestAnimationFrame === 'function') requestAnimationFrame(run)
        else run()
    }

    /**
     * Measures again once a slide has come to rest.
     *
     * The drawer takes its time coming in, and a measurement taken as the class changes reads the
     * position it is leaving rather than the one it is going to, which would leave the ring standing
     * where the navigation used to be.
     */
    function onTransitionEnd() {
        scheduleMeasure()
    }

    function advance() {
        stopSettling()
        if (activeStep.value < steps.value.length) activeStep.value += 1
        scrolledFor = -1
        box.value = null
        measure()
    }

    function onDocumentClick(event: MouseEvent) {
        const current = step.value
        if (!current || current.advance !== 'click') return
        const element = findTarget(current.target)
        if (element && event.target instanceof Node && element.contains(event.target)) advance()
    }

    /**
     * Carries a step that is being filled in, once the reader leaves the field with something in it.
     *
     * Moving between two fields of the same marked form is not leaving it, so a form is filled in
     * peace and keeps its light throughout. Leaving it empty is not leaving it either: the step
     * waits, because nothing has been done yet.
     */
    function onFocusOut(event: FocusEvent) {
        const current = step.value
        // A step to be read carries on the same way, because somebody who has just filled in what was
        // missing has shown they read it, and asking them to confirm afterwards asks twice.
        if (!current || (current.advance !== 'fill' && current.advance !== 'read')) return
        const element = findTarget(current.target)
        if (!element || !(event.target instanceof Node) || !element.contains(event.target)) return
        if (event.relatedTarget instanceof Node && element.contains(event.relatedTarget)) return
        if (!filled(element)) return
        advance()
    }

    function dismiss() {
        guideDismissed.value = true
    }

    /**
     * Watching the whole page for changes is only worth its cost while a task is being walked, so
     * the observer comes and goes with the task rather than running for every visitor.
     */
    function watchPage(active: boolean) {
        observer?.disconnect()
        observer = null
        if (!active) return
        observer = new MutationObserver(() => scheduleMeasure())
        // Attributes as well as children, because a drawer does not leave the page when it closes:
        // it changes one class and slides away. Watching children alone left the ring sitting on the
        // menu button after the reader had already opened the menu.
        observer.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class', 'style'],
        })
    }

    onMounted(() => {
        reducedMotion.value = prefersReducedMotion()
        document.addEventListener('click', onDocumentClick, true)
        document.addEventListener('focusout', onFocusOut, true)
        document.addEventListener('transitionend', onTransitionEnd, true)
        window.addEventListener('resize', measure)
        window.addEventListener('scroll', measure, true)
        watchPage(activeTaskKey.value !== null)
        measure()
    })

    onBeforeUnmount(() => {
        document.removeEventListener('click', onDocumentClick, true)
        document.removeEventListener('focusout', onFocusOut, true)
        document.removeEventListener('transitionend', onTransitionEnd, true)
        window.removeEventListener('resize', measure)
        window.removeEventListener('scroll', measure, true)
        watchPage(false)
        stopSettling()
    })

    watch(
        () => route.name,
        () => {
            if (step.value?.advance === 'route' && onStepRoute.value) advance()
            else measure()
        },
    )

    watch([activeTaskKey, activeStep], () => {
        watchPage(activeTaskKey.value !== null)
        scrolledFor = -1
        // A wait started for the step just left would otherwise stand in the way of the next one's.
        stopSettling()
        measure()
    })

    return {
        box, step, steps, pointing, revealing, blocked, behindMenu, gaze, targetLow, finished, reducedMotion,
        onStepRoute, advance, dismiss,
    }
}

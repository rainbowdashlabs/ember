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

function findTarget(mark: string | undefined): HTMLElement | null {
    if (!mark) return null
    return document.querySelector<HTMLElement>(`[${ATTRIBUTE}="${CSS.escape(mark)}"]`)
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

    const steps = computed<OnboardingStep[]>(() => (activeTaskKey.value ? flowFor(activeTaskKey.value) : []))
    const step = computed<OnboardingStep | null>(() => steps.value[activeStep.value] ?? null)
    const onStepRoute = computed(() => !step.value?.route || route.name === step.value.route)
    const finished = computed(() => steps.value.length > 0 && activeStep.value >= steps.value.length)

    /** Where the reader should look: at the element, or at the page the element is on. */
    const pointing = computed(() => box.value !== null)
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

    function measure() {
        if (!activeTaskKey.value) {
            box.value = null
            revealing.value = false
            return
        }
        const own = findTarget(step.value?.target)
        const element = own ?? findHolder(step.value?.target)
        revealing.value = own === null && element !== null
        if (!element) {
            box.value = null
            if (step.value?.optional && onStepRoute.value) advance()
            return
        }
        const rect = element.getBoundingClientRect()
        box.value = {top: rect.top, left: rect.left, width: rect.width, height: rect.height}
        if (scrolledFor !== activeStep.value) {
            scrolledFor = activeStep.value
            element.scrollIntoView({block: 'center', behavior: reducedMotion.value ? 'auto' : 'smooth'})
        }
    }

    function advance() {
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
        observer = new MutationObserver(() => measure())
        observer.observe(document.body, {childList: true, subtree: true})
    }

    onMounted(() => {
        reducedMotion.value = prefersReducedMotion()
        document.addEventListener('click', onDocumentClick, true)
        window.addEventListener('resize', measure)
        window.addEventListener('scroll', measure, true)
        watchPage(activeTaskKey.value !== null)
        measure()
    })

    onBeforeUnmount(() => {
        document.removeEventListener('click', onDocumentClick, true)
        window.removeEventListener('resize', measure)
        window.removeEventListener('scroll', measure, true)
        watchPage(false)
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
        measure()
    })

    return {box, step, steps, pointing, revealing, gaze, finished, reducedMotion, onStepRoute, advance, dismiss}
}

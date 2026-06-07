/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'
import {useRouter} from 'vue-router'
import {useSession} from './useSession'
import {StationPermission} from '@/api/types'

const STORAGE_KEY = 'onboarding_tour_completed'

const isActive = ref(false)
const currentStep = ref(0)

export interface TourStep {
    id: string
    icon: string[]
    route?: string
    roles?: string[]
}

const ALL_STEPS: TourStep[] = [
    {id: 'welcome', icon: ['fas', 'fire'], route: 'dashboard-overview'},
    {id: 'dashboard', icon: ['fas', 'gauge'], route: 'dashboard-overview'},
    {id: 'news', icon: ['fas', 'newspaper'], route: 'news-list'},
    {id: 'profile', icon: ['fas', 'user'], route: 'profile'},
    {id: 'absences', icon: ['fas', 'calendar-days'], route: 'profile-absences'},
    {id: 'events', icon: ['fas', 'calendar-days'], route: 'events-upcoming'},
    {id: 'inventory', icon: ['fas', 'boxes-stacked'], route: 'inventory-my'},
    {id: 'forms', icon: ['fas', 'square-poll-vertical'], route: 'forms-list'},
    {id: 'knowledgeBase', icon: ['fas', 'book'], route: 'kb-browse'},
    {id: 'lending', icon: ['fas', 'hand-holding'], route: 'inventory-lending', roles: [StationPermission.INVENTORY_MANAGER]},
    {id: 'managedProfiles', icon: ['fas', 'users'], route: 'profile-managed', roles: [StationPermission.MEMBER_GUARDIAN]},
    {id: 'members', icon: ['fas', 'users'], route: 'members-list', roles: [StationPermission.MEMBER_MANAGER]},
    {id: 'attendance', icon: ['fas', 'clipboard-user'], route: 'attendance-new', roles: [StationPermission.ATTENDANCE_MANAGER]},
    {id: 'eventManagement', icon: ['fas', 'calendar-days'], route: 'events', roles: [StationPermission.EVENT_MANAGER]},
    {id: 'federation', icon: ['fas', 'arrow-right-arrow-left'], route: 'station-federation', roles: [StationPermission.STATION_FEDERATION]},
    {id: 'discovery', icon: ['fas', 'compass'], route: 'station-discovery', roles: [StationPermission.STATION_ADMINISTRATOR, StationPermission.STATION_FEDERATION]},
    {id: 'station', icon: ['fas', 'gears'], route: 'station-manage', roles: [StationPermission.STATION_ADMINISTRATOR]},
    {id: 'settings', icon: ['fas', 'gear'], route: 'profile-settings'},
    {id: 'helpCenter', icon: ['fas', 'circle-question'], route: 'dashboard-overview'},
    {id: 'done', icon: ['fas', 'check'], route: 'dashboard-overview'},
]

export function useOnboardingTour() {
    const {hasPermission} = useSession()
    const router = useRouter()

    const filteredSteps = computed(() =>
        ALL_STEPS.filter(step => {
            if (!step.roles) return true
            return step.roles.some(role => hasPermission(role))
        })
    )

    const totalSteps = computed(() => filteredSteps.value.length)
    const currentStepData = computed(() => filteredSteps.value[currentStep.value] ?? null)

    function navigateToStep(step: TourStep) {
        if (step.route) {
            router.push({name: step.route}).catch(() => {})
        }
    }

    function startTour() {
        currentStep.value = 0
        isActive.value = true
        const step = filteredSteps.value[0]
        if (step) navigateToStep(step)
    }

    function nextStep() {
        if (currentStep.value < totalSteps.value - 1) {
            currentStep.value++
            navigateToStep(filteredSteps.value[currentStep.value])
        } else {
            finishTour()
        }
    }

    function prevStep() {
        if (currentStep.value > 0) {
            currentStep.value--
            navigateToStep(filteredSteps.value[currentStep.value])
        }
    }

    function skipTour() {
        finishTour()
    }

    function finishTour() {
        isActive.value = false
        localStorage.setItem(STORAGE_KEY, 'true')
        router.push({name: 'dashboard-overview'}).catch(() => {})
    }

    function isTourCompleted(): boolean {
        return localStorage.getItem(STORAGE_KEY) === 'true'
    }

    function checkFirstLogin() {
        if (!isTourCompleted()) {
            startTour()
        }
    }

    return {
        isActive: readonly(isActive),
        currentStep: readonly(currentStep),
        filteredSteps,
        totalSteps,
        currentStepData,
        startTour,
        nextStep,
        prevStep,
        skipTour,
        checkFirstLogin,
    }
}

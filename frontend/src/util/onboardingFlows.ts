/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationModule, StationPermission} from '@/api/generated/schema'

/**
 * One step of a task: what should light up, and what carries the guide on to the next one.
 *
 * A step marked optional is passed over when its target is not on the page. Some elements only
 * appear when there is a choice to make, and pointing at an element that is not there is worse than
 * pointing at nothing.
 */
export interface OnboardingStep {
    /** The mark carried by the element that should light up. */
    target?: string
    /** The route the step happens on. Reaching it ends a step that has no target. */
    route?: string
    /**
     * What ends the step.
     *
     * `click` and `route` end it on the reader's click and on arriving at a page. `fill` is for
     * anything that has to be typed into: clicking into a field is the start of the work and not the
     * end of it, so such a step keeps its light until the reader leaves the field with something in
     * it. A step that moved on at the click would darken the very field being filled.
     *
     * `read` ends on the reader saying they have read it. Some tasks are there to show where a thing
     * is and what it means, and demanding a click to prove it turns looking into doing: a walk that
     * only ends once somebody has signed up for a Tuesday has made them sign up for a Tuesday. Such a
     * step points at an example where there is one and stands on its own text where there is not.
     *
     * It also ends on something being typed into what it points at, which is what lets one step serve
     * a reader with nothing to change and a reader with a gap to fill. Somebody looking over a
     * profile that is already right says so and moves on; somebody filling in what was missing has
     * shown the same thing by doing it, and being asked to confirm afterwards would be asking twice.
     */
    advance: 'click' | 'route' | 'fill' | 'read'
    optional?: boolean
    /**
     * What the step's text is found under. Steps without one are counted, which is fine for a walk
     * that is the same for everybody; a walk that leaves steps out for some readers names its steps,
     * so leaving one out does not shift the words of the rest.
     */
    id?: string
    /** Modules of which at least one has to run for the step to be shown. */
    modules?: StationModule[]
    /** Rights of which the reader needs at least one for the step to be shown. */
    permissions?: StationPermission[]
}

/** What decides whether a reader is shown a step that names modules or rights. */
export interface StepReader {
    runs: (module: StationModule) => boolean
    can: (permission: StationPermission) => boolean
}

/**
 * The walk past the station's areas. Every stop points at an entry of the sidebar and explains it;
 * a stop about a switched-off area, or one the reader has no right to, is left out.
 */
const TOUR: OnboardingStep[] = [
    {id: 'welcome', advance: 'read'},
    {id: 'dashboard', target: 'nav.dashboard', advance: 'read', optional: true},
    {id: 'news', target: 'nav.news', advance: 'read', optional: true, modules: [StationModule.NEWS]},
    {id: 'profile', target: 'nav.profile', advance: 'read', optional: true},
    {
        id: 'absences', target: 'nav.profile.absences', advance: 'read', optional: true,
        modules: [StationModule.EVENTS, StationModule.ATTENDANCE],
    },
    {id: 'events', target: 'nav.events.upcoming', advance: 'read', optional: true, modules: [StationModule.EVENTS]},
    {id: 'inventory', target: 'nav.inventory', advance: 'read', optional: true, modules: [StationModule.INVENTORY]},
    {id: 'forms', target: 'nav.forms', advance: 'read', optional: true, modules: [StationModule.FORMS]},
    {
        id: 'knowledgeBase', target: 'nav.knowledge', advance: 'read', optional: true,
        modules: [StationModule.KNOWLEDGE_BASE],
    },
    {
        id: 'lending', target: 'nav.inventory.lending', advance: 'read', optional: true,
        modules: [StationModule.INVENTORY_LENDING],
        permissions: [StationPermission.INVENTORY_LENDING_REQUEST, StationPermission.INVENTORY_LENDING_MANAGER],
    },
    {
        id: 'managedProfiles', target: 'nav.profile.managed', advance: 'read', optional: true,
        permissions: [StationPermission.MEMBER_GUARDIAN],
    },
    {
        id: 'members', target: 'nav.members', advance: 'read', optional: true,
        permissions: [StationPermission.MEMBER_MANAGER],
    },
    {
        id: 'attendance', target: 'nav.attendance', advance: 'read', optional: true,
        modules: [StationModule.ATTENDANCE], permissions: [StationPermission.ATTENDANCE_MANAGER],
    },
    {
        id: 'eventManagement', target: 'nav.events', advance: 'read', optional: true,
        modules: [StationModule.EVENTS], permissions: [StationPermission.EVENT_MANAGER],
    },
    {
        id: 'federation', target: 'nav.federation', advance: 'read', optional: true,
        permissions: [StationPermission.STATION_FEDERATION],
    },
    {
        id: 'station', target: 'nav.manage', advance: 'read', optional: true,
        permissions: [StationPermission.STATION_ADMINISTRATOR],
    },
    {id: 'helpCenter', advance: 'read'},
]

/**
 * The steps of every task that has any, by the key the backend gives it.
 *
 * A task with no entry here is one that happens outside Ember, such as putting a bookmark in a
 * browser. Those show their text and are ticked off, with nothing to point at.
 */
export const ONBOARDING_FLOWS: Record<string, OnboardingStep[]> = {
    'member.tour': TOUR,
    'member.profile': [
        {target: 'nav.profile', route: 'profile', advance: 'route'},
        {target: 'profile.fields', advance: 'read'},
    ],
    'member.notifications': [
        {target: 'nav.profile.notifications', route: 'profile-notifications', advance: 'route'},
        {target: 'notifications.matrix', advance: 'click'},
    ],
    'member.push': [
        {target: 'nav.profile.notifications', route: 'profile-notifications', advance: 'route'},
        {target: 'push.card', advance: 'read'},
        {target: 'push.enable', advance: 'click', optional: true},
    ],
    'member.eventAnswer': [
        {target: 'nav.events.upcoming', route: 'events-upcoming', advance: 'route'},
        {target: 'events.item.registration', advance: 'read'},
        {target: 'events.item.attendance', advance: 'read'},
    ],
    'member.calendar': [
        {target: 'nav.profile.notifications', route: 'profile-notifications', advance: 'route'},
        {target: 'feed.create', advance: 'click', optional: true},
        {target: 'feed.ical', advance: 'click'},
    ],
    'member.absence': [
        {target: 'nav.profile.absences', route: 'profile-absences', advance: 'route'},
        {target: 'absences.add', advance: 'click'},
    ],
    'member.wiki': [
        {target: 'nav.knowledge', route: 'kb-browse', advance: 'route'},
        {target: 'knowledge.first-entry', advance: 'click'},
    ],
    'member.quiz': [
        {target: 'nav.quiz.training', route: 'quiz-training', advance: 'route'},
        {target: 'quiz.catalogs', advance: 'click'},
        {target: 'quiz.start', advance: 'click'},
    ],

    'guardian.profile': [
        {target: 'nav.profile.managed', route: 'profile-managed', advance: 'route'},
        {target: 'managed.member-select', advance: 'click', optional: true},
        {target: 'managed.fields.save', advance: 'click'},
    ],
    'guardian.username': [
        {target: 'nav.profile.managed', route: 'profile-managed', advance: 'route'},
        {target: 'managed.member-select', advance: 'click', optional: true},
        {target: 'managed.access.username', advance: 'fill'},
        {target: 'managed.access.username-save', advance: 'click'},
    ],
    'guardian.login': [
        {target: 'nav.profile.managed', route: 'profile-managed', advance: 'route'},
        {target: 'managed.access.login-toggle', advance: 'click'},
    ],
    'guardian.password': [
        {target: 'nav.profile.managed', route: 'profile-managed', advance: 'route'},
        {target: 'managed.access.password', advance: 'fill'},
        {target: 'managed.access.password-save', advance: 'click'},
    ],
    'guardian.eventAnswer': [
        {target: 'nav.events.upcoming', route: 'events-upcoming', advance: 'route'},
        {target: 'events.item.pending', advance: 'click'},
    ],

    'station.groups': [
        {target: 'nav.members.groups', route: 'members-groups', advance: 'route'},
        {target: 'groups.add', advance: 'click'},
    ],
    'station.mail': [
        {target: 'nav.manage.mailing', route: 'station-mailing', advance: 'route'},
        {target: 'mailing.add-provider', advance: 'click'},
        {target: 'mailing.test', advance: 'click'},
    ],
    'station.branding': [
        {target: 'nav.manage', route: 'station-manage', advance: 'route'},
        {target: 'theme.logo', advance: 'click'},
    ],
    'station.firstEvent': [
        {target: 'nav.events', route: 'events', advance: 'route'},
        {target: 'events.create', advance: 'click'},
    ],
    'station.kbSeed': [
        {target: 'nav.knowledge', route: 'kb-browse', advance: 'route'},
        {target: 'knowledge.create', advance: 'click'},
    ],
    'station.federation': [
        {target: 'nav.federation', route: 'station-federation-settings', advance: 'route'},
        {target: 'federation.visibility', advance: 'click'},
    ],
    'station.invites': [
        {target: 'nav.members.create', route: 'members-create', advance: 'route'},
    ],
    'station.memberTypes': [
        {target: 'nav.members.type-permissions', route: 'members-type-permissions', advance: 'route'},
    ],

    'instance.ownAccount': [
        {target: 'account.email', route: 'account-avatar', advance: 'route'},
        {target: 'account.two-factor', route: 'account-security', advance: 'route'},
    ],
    'instance.legal': [
        {target: 'nav.admin.legal', route: 'admin-legal', advance: 'route'},
        {target: 'legal.template', advance: 'click'},
    ],
    'instance.mail': [
        {target: 'nav.admin.mailing', route: 'admin-mailing', advance: 'route'},
        {target: 'mailing.add-provider', advance: 'click'},
        {target: 'mailing.test', advance: 'click'},
    ],
    'instance.firstStation': [
        {target: 'nav.admin.stations', route: 'admin-stations', advance: 'route'},
        {target: 'admin.stations.create', advance: 'click'},
    ],
    'instance.stationRegistration': [
        {target: 'nav.admin.settings', route: 'admin-settings', advance: 'route'},
        {target: 'admin.settings.station-registration', advance: 'click'},
    ],
    'instance.security': [
        {target: 'nav.admin.security', route: 'admin-security', advance: 'route'},
    ],
    'instance.storage': [
        {target: 'nav.admin.storage', route: 'admin-storage', advance: 'route'},
    ],
    'instance.operations': [
        {target: 'nav.admin.maps', route: 'admin-maps', advance: 'route'},
    ],
}

/**
 * The steps of a task, or an empty list for one that happens outside Ember.
 *
 * @param reader who walks it; with one, the steps about switched-off modules or rights the reader
 *               does not hold are left out
 */
export function flowFor(taskKey: string, reader?: StepReader): OnboardingStep[] {
    const steps = ONBOARDING_FLOWS[taskKey] ?? []
    return reader ? steps.filter(step => shownTo(step, reader)) : steps
}

function shownTo(step: OnboardingStep, reader: StepReader): boolean {
    if (step.modules && !step.modules.some(reader.runs)) return false
    return !step.permissions || step.permissions.some(reader.can)
}

/** The words of a step, by its name where it has one and by its place otherwise. */
export function stepTextKey(taskKey: string, step: OnboardingStep, index: number): string {
    return `onboarding.steps.${taskKey}.${step.id ?? index}`
}

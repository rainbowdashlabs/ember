/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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
     */
    advance: 'click' | 'route' | 'fill'
    optional?: boolean
}

/**
 * The steps of every task that has any, by the key the backend gives it.
 *
 * A task with no entry here is one that happens outside Ember, such as putting a bookmark in a
 * browser. Those show their text and are ticked off, with nothing to point at.
 */
export const ONBOARDING_FLOWS: Record<string, OnboardingStep[]> = {
    'member.profile': [
        {target: 'nav.profile', route: 'profile', advance: 'route'},
        {target: 'profile.fields', advance: 'fill', optional: true},
        {target: 'profile.save', advance: 'click'},
    ],
    'member.notifications': [
        {target: 'nav.profile.notifications', route: 'profile-notifications', advance: 'route'},
        {target: 'notifications.matrix', advance: 'click'},
    ],
    'member.eventAnswer': [
        {target: 'nav.events.upcoming', route: 'events-upcoming', advance: 'route'},
        {target: 'events.item.pending', advance: 'click'},
        {target: 'events.registration-fields.submit', advance: 'click', optional: true},
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
        {target: 'events.item.member-select', advance: 'click', optional: true},
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

/** The steps of a task, or an empty list for one that happens outside Ember. */
export function flowFor(taskKey: string): OnboardingStep[] {
    return ONBOARDING_FLOWS[taskKey] ?? []
}

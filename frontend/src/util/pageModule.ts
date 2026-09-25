/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Which part of the product a page belongs to, read off its address.
 *
 * <p>A browser tab shows the first few words of a title and nothing else, and a title that is only
 * the thing it shows leaves a reader with a row of names and no way to tell which of them is a
 * ticket, a file or a member. Naming the part it came from is what separates a dozen open tabs, and
 * it is what a bookmark and a history entry carry too.
 *
 * <p>Every address that matches is named, closest first, so a section inside an area says both: an
 * association's appointment reads "Termine" and then "Verband", which is what tells it apart from
 * the station's own appointment page drawn by the very same view. An address not listed here has no
 * part to name and is left with its own title alone, which is right for the pages that stand
 * outside the product's sections.
 */
const MODULES: ReadonlyArray<readonly [string, string]> = [
    ['/station/attendance', 'pageModule.attendance'],
    ['/station/boards', 'pageModule.boards'],
    ['/station/checklist', 'pageModule.checklists'],
    ['/station/dashboard', 'pageModule.dashboard'],
    ['/station/discovery', 'pageModule.discovery'],
    ['/station/events', 'pageModule.events'],
    ['/station/federate', 'pageModule.federation'],
    ['/station/federation', 'pageModule.federation'],
    ['/station/federation/boards', 'pageModule.boards'],
    ['/station/federation/events', 'pageModule.events'],
    ['/station/federation/knowledge', 'pageModule.knowledge'],
    ['/station/federation/news', 'pageModule.news'],
    ['/station/federation/protocols', 'pageModule.protocols'],
    ['/station/federation/quiz', 'pageModule.quiz'],
    ['/station/forms', 'pageModule.forms'],
    ['/station/inventory', 'pageModule.inventory'],
    ['/station/knowledge', 'pageModule.knowledge'],
    ['/station/lost-and-found', 'pageModule.lostAndFound'],
    ['/station/manage', 'pageModule.manage'],
    ['/station/media', 'pageModule.media'],
    ['/station/members', 'pageModule.members'],
    ['/station/monitoring', 'pageModule.monitoring'],
    ['/station/news', 'pageModule.news'],
    ['/station/pages', 'pageModule.pages'],
    ['/station/procedures', 'pageModule.procedures'],
    ['/station/profile', 'pageModule.profile'],
    ['/station/protocols', 'pageModule.protocols'],
    ['/station/quiz', 'pageModule.quiz'],
    ['/station/requirements', 'pageModule.requirements'],
    ['/station/setup', 'pageModule.setup'],
    ['/cluster/applications', 'pageModule.applications'],
    ['/cluster/events', 'pageModule.events'],
    ['/cluster/inventory', 'pageModule.inventory'],
    ['/cluster/knowledge', 'pageModule.knowledge'],
    ['/cluster/members', 'pageModule.members'],
    ['/cluster/news', 'pageModule.news'],
    ['/cluster/stations', 'pageModule.stations'],
    ['/cluster/storage', 'pageModule.storage'],
    ['/cluster/team', 'pageModule.team'],
    ['/account', 'pageModule.account'],
    ['/admin', 'pageModule.admin'],
    ['/cluster', 'pageModule.cluster'],
    ['/helpcenter', 'pageModule.helpCenter'],
]

/**
 * The translation keys naming the parts a page belongs to, closest first, empty where it has none.
 *
 * <p>Matching is on whole segments, so `/station/newsletter` is not taken for `/station/news`.
 */
export function pageModuleKeys(path: string): string[] {
    return MODULES
        .filter(([prefix]) => path === prefix || path.startsWith(`${prefix}/`))
        .sort(([left], [right]) => right.length - left.length)
        .map(([, key]) => key)
}

/**
 * A page's title with the parts it belongs to after it, which is what the browser tab reads.
 *
 * <p>A part is left off where the page's own title already says it, because "Boards - Boards" says
 * nothing twice, and the whole thing is left off where there is no title yet, because a tab reading
 * only the section name during a load is worse than one that waits.
 */
export function titleWithModule(title: string, moduleNames: string[]): string {
    if (!title) return ''
    return [title, ...moduleNames.filter(name => name !== title)].join(' - ')
}

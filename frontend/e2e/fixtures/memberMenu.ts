/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Locator, type Page} from '@playwright/test'

/**
 * Driving the one member menu, wherever a story meets it.
 *
 * <p>Every place the product asks which member is this menu, so a story that puts somebody into a
 * group and a story that assigns a ticket press the same three things. Each of them writing its own
 * open-type-press is how the selectors drifted apart the last time.
 *
 * <p>`scope` is the page where only one menu is on it, and the panel or the modal around it where
 * there is more than one. The menu draws its trigger and its rows inside one element, so scoping to
 * a container is enough to tell two of them apart.
 */
function trigger(scope: Page | Locator): Locator {
    return scope.getByTestId('member-select-trigger').first()
}

function rows(scope: Page | Locator): Locator {
    return scope.getByTestId('member-select-option')
}

/**
 * Opens the menu and waits for its rows to be reachable.
 *
 * <p>A menu that is already open is left alone rather than pressed: the ticket's assignee field opens
 * its menu the moment the field is clicked, and pressing the trigger there would shut it again.
 */
export async function openMemberMenu(scope: Page | Locator): Promise<void> {
    const panel = scope.getByTestId('member-select-panel')
    if (!(await panel.isVisible())) await trigger(scope).click()
    await expect(panel).toBeVisible()
}

/** The name a row shows, read off the element that draws it rather than guessed from the row's text. */
function nameOf(row: Locator): Promise<string> {
    return row.getByTestId('member-name').first().innerText().then(text => text.trim())
}

/**
 * The people the menu is offering right now, in the order it lists them.
 *
 * <p>The empty answer carries a test id of its own and is not one of them, so a menu that may be
 * emptied counts the same as one that may not.
 */
export async function offeredMembers(scope: Page | Locator): Promise<string[]> {
    const offered = await rows(scope).all()
    return Promise.all(offered.map(nameOf))
}

/**
 * Searches for somebody by name and takes them.
 *
 * <p>The row is pressed rather than taken with Enter: a menu that may be emptied opens with the empty
 * answer highlighted, so Enter there would clear the choice instead of making one. The keyboard is
 * asserted where it is the subject, in the board's assignee story.
 *
 * @param scope where the menu is
 * @param name  what to type, which is matched against the name and the address
 */
export async function pickMemberByName(scope: Page | Locator, name: string): Promise<void> {
    await openMemberMenu(scope)
    await scope.getByTestId('member-select-search').getByRole('searchbox').fill(name)
    const wanted = rows(scope).filter({hasText: name}).first()
    await expect(wanted).toBeVisible()
    await wanted.click()
}

/**
 * Takes whoever the menu offers first, and answers with their name.
 *
 * <p>For the stories that need somebody rather than anybody in particular, which is most of them:
 * the seeded station is not theirs to choose from.
 */
export async function pickFirstMember(scope: Page | Locator): Promise<string> {
    await openMemberMenu(scope)
    const first = rows(scope).first()
    await expect(first).toBeVisible()
    const name = await nameOf(first)
    await first.click()
    return name
}

/** Whoever the menu is holding, as its closed trigger reads them. */
export async function chosenMember(scope: Page | Locator): Promise<string> {
    return (await trigger(scope).innerText()).trim()
}

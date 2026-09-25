/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether a press inside a row landed on one of the row's own controls rather than on the row.
 *
 * <p>A row that opens a page nearly always carries an acknowledge, a delete or a download as well,
 * and a press on one of those belongs to the button alone. `@click.stop` on the button does not say
 * that: stopping the event travelling upward leaves the browser free to follow the link the press
 * happened inside, so a delete button in a link deletes and then navigates. The row therefore asks
 * once what was actually pressed, and no button has to remember anything.
 *
 * <p>A row that is not a link needs the same answer for the other direction: its own click handler
 * must stay out of a press meant for a button sitting in it.
 */
export function pressedAControl(event: MouseEvent): boolean {
    const pressed = event.target as HTMLElement | null
    return !!pressed?.closest('button, input, select, textarea, label, [role="button"]')
}

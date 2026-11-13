/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The colours an event group without one of its own is marked with, mostly taken from the station's
 * colour scheme. Neighbouring ids get different colours, so two event groups side by side rarely look
 * alike. The scheme's yellow is left out, since a narrow band of it all but vanishes on a light page.
 */
export const EVENT_GROUP_PALETTE: readonly string[] = ['#ff6421', '#3694ff', '#00c507', '#c71100', '#73ceff', '#9333ea']

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/

/**
 * The colour that marks the parts of an event group: its own where it has a valid one, otherwise the
 * palette colour its id picks. A value of any other shape, which a partner instance could send, is
 * never used as a style.
 */
export function eventGroupColor(eventGroupId: number, color: string | null | undefined): string {
  if (color && HEX_COLOR.test(color)) return color
  return EVENT_GROUP_PALETTE[Math.abs(eventGroupId) % EVENT_GROUP_PALETTE.length]!
}

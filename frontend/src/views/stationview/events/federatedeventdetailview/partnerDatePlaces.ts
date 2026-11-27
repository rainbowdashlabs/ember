/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {PartnerEventDetail} from '@/api/generated/schema'

/** How a sign-up for the date of a partner's appointment would land, as far as its places show. */
export interface PartnerDateState {
  /** Every place of the date is taken. */
  full: boolean
  /** A sign-up now goes onto the waiting list rather than straight onto the list. */
  joinsQueue: boolean
}

const OPEN: PartnerDateState = {full: false, joinsQueue: false}

/**
 * Reads the places the partner counted for the date a sign-up is filed under.
 *
 * <p>The partner counts the date it has in view; where that is another date than the one signed up
 * for, nothing is shown. A sign-up lands on the waiting list where the date is full or others already
 * wait, unless somebody decides about it first: an appointment that asks for confirmation, or a partner
 * that handed this station the choice, holds it for a decision instead.
 */
export function partnerDateState(detail: PartnerEventDetail | null, eventDate: string): PartnerDateState {
  const places = detail?.datePlaces
  if (!detail || !places || places.date !== eventDate) return OPEN
  const full = places.limit !== null && places.taken >= places.limit
  const decidedFirst = detail.event.requiresConfirmation || !!detail.places?.decidesItself
  return {full, joinsQueue: !decidedFirst && (full || places.waiting > 0)}
}

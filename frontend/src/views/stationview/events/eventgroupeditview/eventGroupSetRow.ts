/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventGroupSetDraft} from '@/api/generated/schema'

/** A set as the event group editor holds it, every part filled in: an id for a stored set, null for a new one. */
export type SetRow = Required<EventGroupSetDraft>

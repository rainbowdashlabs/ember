/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {QueueEntry} from '@/util/registrationQueue'

/** A short waiting list as the help pages show it: two members of the station and a partner's member. */
export const SAMPLE_QUEUE: QueueEntry[] = [
    {kind: 'member', id: 1, position: 1, identity: null, name: 'Jonas Becker', partnerId: null},
    {kind: 'partner', id: 2, position: 2, identity: null, name: 'Mia Wagner', partnerId: 7},
    {kind: 'member', id: 3, position: 3, identity: null, name: 'Paul Fischer', partnerId: null},
]

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TicketPriority, type TicketPriorityName} from '@/api/boards'

/**
 * Returns the FontAwesome icon tuple for a ticket priority, with a neutral
 * fallback for unknown values.
 */
export function priorityIcon(priority: TicketPriorityName): string[] {
    switch (priority) {
        case TicketPriority.HIGHEST:
            return ['fas', 'angles-up']
        case TicketPriority.HIGH:
            return ['fas', 'angle-up']
        case TicketPriority.MEDIUM:
            return ['fas', 'equals']
        case TicketPriority.LOW:
            return ['fas', 'angle-down']
        case TicketPriority.LOWEST:
            return ['fas', 'angles-down']
        default:
            return ['fas', 'minus']
    }
}

/**
 * Returns the tailwind text-color class that visually pairs with the
 * {@link priorityIcon} for the same priority, with a neutral fallback.
 */
export function priorityColor(priority: TicketPriorityName): string {
    switch (priority) {
        case TicketPriority.HIGHEST:
            return 'text-red-500'
        case TicketPriority.HIGH:
            return 'text-orange-500'
        case TicketPriority.MEDIUM:
            return 'text-yellow-500'
        case TicketPriority.LOW:
            return 'text-blue-400'
        case TicketPriority.LOWEST:
            return 'text-gray-400'
        default:
            return 'text-gray-400'
    }
}

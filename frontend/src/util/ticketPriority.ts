/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TicketPriority, type TicketPriorityName} from '@/api/boards'
import {enumOptions, type ColumnOption} from '@/components/table/tableColumn'

const PRIORITY_LABEL_KEYS: Record<TicketPriorityName, string> = {
    [TicketPriority.LOWEST]: 'boards.priorityLowest',
    [TicketPriority.LOW]: 'boards.priorityLow',
    [TicketPriority.MEDIUM]: 'boards.priorityMedium',
    [TicketPriority.HIGH]: 'boards.priorityHigh',
    [TicketPriority.HIGHEST]: 'boards.priorityHighest',
}

/** Every ticket priority, lowest first. */
export const ticketPriorities = Object.keys(PRIORITY_LABEL_KEYS) as TicketPriorityName[]

/** Every ticket priority with its label, lowest first. */
export function priorityOptions(t: (key: string) => string): ColumnOption[] {
    return enumOptions(ticketPriorities, priority => t(PRIORITY_LABEL_KEYS[priority as TicketPriorityName]))
}

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

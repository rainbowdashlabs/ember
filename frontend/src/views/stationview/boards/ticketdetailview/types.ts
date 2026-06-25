/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * UI option for the ticket-priority picker (priority value + display strings + colour token).
 * Shared by the body, left/right columns, and the priority field so the same priority list can
 * be threaded through props without redeclaring the shape per consumer.
 */
export interface PriorityOption {
    value: string
    label: string
    icon: string[]
    color?: string
}

/**
 * Hit returned by the in-ticket knowledge-base search picker. Shared by the body, left column,
 * and the KB-links section.
 */
export interface KbSearchResult {
    id: number
    title: string
    path: string
}

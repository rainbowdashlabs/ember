/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Accent} from './pitchTypes'

/**
 * Tailwind cannot see a class it never reads literally, so the accents are spelled out per use
 * rather than built from the accent name.
 */
const TEXT: Record<Accent, string> = {
    primary: 'text-primary',
    secondary: 'text-secondary',
    success: 'text-success',
    info: 'text-info-accent',
    error: 'text-error',
}

const BORDER: Record<Accent, string> = {
    primary: 'border-primary',
    secondary: 'border-secondary',
    success: 'border-success',
    info: 'border-info-accent',
    error: 'border-error',
}

const SOFT_BG: Record<Accent, string> = {
    primary: 'bg-primary/10',
    secondary: 'bg-secondary/10',
    success: 'bg-success/10',
    info: 'bg-info-accent/10',
    error: 'bg-error/10',
}

export function accentText(accent?: Accent): string {
    return TEXT[accent ?? 'primary']
}

export function accentBorder(accent?: Accent): string {
    return BORDER[accent ?? 'primary']
}

export function accentSoftBg(accent?: Accent): string {
    return SOFT_BG[accent ?? 'primary']
}

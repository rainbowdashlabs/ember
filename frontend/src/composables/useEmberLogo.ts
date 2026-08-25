/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { LogoLayer, EyeDirection } from '@/components/display/LayeredEmberLogo.vue'

const allLayers: LogoLayer[] = [
    { name: 'fire_glow', label: 'Glow' },
    { name: 'fire_blank', label: 'Flame' },
    { name: 'fire_blush', label: 'Blush' },
    { name: 'fire_eyes_left', label: 'Left Open' },
    { name: 'fire_eyes_left_half', label: 'Left Half' },
    { name: 'fire_blink_left', label: 'Left Blink' },
    { name: 'fire_eyes_mid', label: 'Mid Open' },
    { name: 'fire_eyes_mid_half', label: 'Mid Half' },
    { name: 'fire_blink', label: 'Mid Blink' },
    { name: 'fire_eyes_right', label: 'Right Open' },
    { name: 'fire_eyes_right_half', label: 'Right Half' },
    { name: 'fire_blink_right', label: 'Right Blink' },
    { name: 'fire_faq', label: 'FAQ' },
    { name: 'fire_woah_one', label: 'Woah 1' },
    { name: 'fire_woah_two', label: 'Woah 2' },
]

/** Standard ember logo: glow + flame + eyes looking forward */
export function emberLogo() {
    return {
        layers: allLayers,
        activeLayers: new Set(['fire_glow', 'fire_blank', 'fire_eyes_mid']),
    }
}

/** FAQ variant: standard + question mark */
export function emberLogoFaq() {
    return {
        layers: allLayers,
        activeLayers: new Set(['fire_glow', 'fire_blank', 'fire_eyes_mid', 'fire_faq']),
    }
}

/** No-glow variant for dark backgrounds */
export function emberLogoNoGlow() {
    return {
        layers: allLayers,
        activeLayers: new Set(['fire_blank', 'fire_eyes_mid']),
    }
}

/** All available gaze positions for auto-gaze feature */
export const defaultGazePositions: EyeDirection[] = ['left', 'mid', 'right']

/** How Ember looks while guiding somebody through a task. */
export type EmberMood = 'plain' | 'glow' | 'faq' | 'cheer' | 'sober'

/**
 * Ember as the one doing the talking, rather than as a logo.
 *
 * The glow goes with a lit-up target so the figure and the ring read as one thing, the question mark
 * with an explanation, and the cheer with a task just finished. Sober drops the blush for the parts
 * of the instance settings where a bashful flame would be out of place.
 */
export function emberGuide(mood: EmberMood = 'plain') {
    const activeLayers = new Set(['fire_blank', 'fire_eyes_mid', 'fire_blush'])
    if (mood === 'glow' || mood === 'cheer') activeLayers.add('fire_glow')
    if (mood === 'faq') activeLayers.add('fire_faq')
    if (mood === 'cheer') activeLayers.add('fire_woah_one')
    if (mood === 'sober') activeLayers.delete('fire_blush')
    return {layers: allLayers, activeLayers}
}

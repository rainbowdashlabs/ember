/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

/**
 * Reactive header state shared across the app. {@code ViewContent} writes to it
 * from its {@code title} / {@code subtitle} props; the outer layout components
 * ({@code AdminView}, {@code StationView}, {@code HelpcenterView}) read from it
 * and forward the values to their sidebar/header chrome. The browser tab title
 * uses the same source via {@code usePageTitle}.
 */
const title = ref('')
const subtitle = ref('')

export function usePageHeader() {
    return {title, subtitle}
}

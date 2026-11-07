/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {watch, type Ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

/** The key of a tab, which is all the address needs of it. */
interface KeyedTab {
    key: string
}

/** The tab key an address hash names, without its `#` and in the case it was typed. */
function hashKey(hash: string): string {
    const raw = hash.replace(/^#/, '')
    try {
        return decodeURIComponent(raw)
    } catch {
        return raw
    }
}

/**
 * Ties a tab list to the hash of the address, so a tab can be linked to and the address always names
 * the tab on the screen.
 *
 * <p>The hash is matched without regard to case, because people type `#Inventory` as often as
 * `#inventory`. A hash naming a tab that is not there yet is kept and honoured once the tab appears,
 * since tabs behind a permission or a load only arrive after the page does. A hash naming no tab at all
 * leaves the view's own choice alone.
 *
 * <p>The hash is written only when somebody picks a tab, and replaces the current entry rather than
 * adding one: a view settling on its own default must not overwrite the tab a link asked for, and
 * clicking through five tabs must not take five presses of Back to leave the page.
 *
 * @param selected the key of the tab on the screen
 * @param tabs     the tabs currently offered
 * @returns what to call when somebody picks a tab
 */
export function useLinkedTab(selected: Ref<string>, tabs: () => KeyedTab[]) {
    const route = useRoute()
    const router = useRouter()

    function requestedKey(): string | null {
        const wanted = hashKey(route.hash).toLowerCase()
        if (!wanted) return null
        return tabs().find(tab => tab.key.toLowerCase() === wanted)?.key ?? null
    }

    watch(
        [() => route.hash, () => tabs().map(tab => tab.key).join('\n')],
        () => {
            const key = requestedKey()
            if (key !== null && key !== selected.value) selected.value = key
        },
        {immediate: true},
    )

    function picked(key: string) {
        if (hashKey(route.hash) === key) return
        void router.replace({path: route.path, query: route.query, hash: `#${encodeURIComponent(key)}`})
    }

    return {picked}
}

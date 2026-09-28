/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref} from 'vue'
import {onBeforeRouteLeave, type RouteLocationRaw} from 'vue-router'
import {clearDraft, readDraft, saveDraft, type PageDraft as StoredDraft} from '@/util/pageDrafts'
import type {PageDraft} from './types'

/** How long the editing pauses before the list is written to the browser. */
const QUIET_MS = 1000

/**
 * Keeps the pages and questions of a form that are not saved yet, and asks before they are left behind.
 *
 * <p>Everything else in the form editor saves itself; the questions wait for the button. So the list
 * is kept in the browser while it differs from what the server holds, and opening the editor again
 * offers it back with the banner the page editor uses for its drafts: restore or discard, never
 * restored silently, since a colleague may have changed the form in the meantime. Leaving the editor
 * with a changed list asks first, both inside the application and when the tab is closed.
 *
 * @param key   what is being edited, the form's id or the kind of form being created
 * @param pages the pages the editor holds, watched for changes
 */
export function useUnsavedLayout(key: () => string, pages: Ref<PageDraft[]>) {
    const stored = ref('')
    const offered = shallowRef<StoredDraft<PageDraft[]> | null>(null)
    const leaving = ref(false)
    const askingToLeave = ref(false)
    let pending: RouteLocationRaw | null = null
    let timer: ReturnType<typeof setTimeout> | null = null

    const current = computed(() => JSON.stringify(pages.value))

    /** Whether the editor holds pages or questions the server does not. */
    const dirty = computed(() => stored.value !== '' && current.value !== stored.value)

    /**
     * Takes the list as the server holds it, once it is read or saved, and offers a kept list where one
     * says something else.
     *
     * @param offer whether to offer a kept list, which is only worth doing when the editor opens
     */
    function settle(offer = false) {
        stored.value = current.value
        const kept = readDraft<PageDraft[]>(key())
        if (offer && kept && JSON.stringify(kept.content) !== stored.value) offered.value = kept
        else clearDraft(key())
    }

    function restore() {
        if (offered.value) pages.value = offered.value.content
        offered.value = null
    }

    function discard() {
        offered.value = null
        clearDraft(key())
    }

    watch(current, () => {
        if (!stored.value) return
        if (timer) clearTimeout(timer)
        timer = setTimeout(() => {
            if (dirty.value) saveDraft(key(), pages.value)
            else clearDraft(key())
        }, QUIET_MS)
    })

    onBeforeRouteLeave(to => {
        if (!dirty.value || leaving.value) return true
        pending = to
        askingToLeave.value = true
        return false
    })

    /** Where the reader wanted to go before being asked, once they agreed to leave the changes behind. */
    function leaveAnyway(): RouteLocationRaw | null {
        leaving.value = true
        askingToLeave.value = false
        return pending
    }

    /** Lets the next navigation through without asking, which is what a finished save needs. */
    function allowLeaving() {
        leaving.value = true
    }

    function onBeforeUnload(event: BeforeUnloadEvent) {
        if (!dirty.value) return
        event.preventDefault()
    }

    onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
    onBeforeUnmount(() => {
        window.removeEventListener('beforeunload', onBeforeUnload)
        if (timer) clearTimeout(timer)
    })

    return {dirty, offered, askingToLeave, settle, restore, discard, leaveAnyway, allowLeaving}
}

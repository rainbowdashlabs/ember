/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onUnmounted, shallowRef, watch, type Ref} from 'vue'
import {clearDraft, readDraft, saveDraft, type PageDraft} from '@/util/pageDrafts'

/**
 * How long the typing pauses before a draft is written.
 *
 * <p>A block editor changes on every keystroke, and writing to storage on each one would serialise
 * the whole page hundreds of times a minute. A second of quiet is long enough to be cheap and short
 * enough that a closed tab loses nothing worth having.
 */
const QUIET_MS = 1000

/**
 * Keeps what somebody is writing, so leaving the page by accident does not throw it away.
 *
 * <p>A draft found on opening is offered rather than applied. What the server holds is the truth,
 * and a draft is only worth more than it where the reader says so: applying a week-old one over a
 * page somebody has since corrected would lose more than it saves.
 *
 * @param keyOf    names the thing being edited, or nothing where drafts do not apply
 * @param content  what the editor holds, watched for changes
 * @param readOnly whether this is a preview, which never writes a draft
 */
export function useContentDraft<T>(
    keyOf: () => string | undefined,
    content: Ref<T>,
    readOnly: () => boolean,
) {
    const draft = shallowRef<PageDraft<T> | null>(null)
    let timer: ReturnType<typeof setTimeout> | null = null

    const key = keyOf()
    if (key && !readOnly()) {
        draft.value = readDraft<T>(key)
    }

    /** Writes once the typing stops, so a page is serialised on a pause rather than on a keystroke. */
    function keep() {
        const current = keyOf()
        if (!current || readOnly()) return
        if (timer) clearTimeout(timer)
        timer = setTimeout(() => saveDraft(current, content.value), QUIET_MS)
    }

    /** Forgets the draft, which is what saving the real thing, or discarding the draft, amounts to. */
    function forget() {
        const current = keyOf()
        if (timer) clearTimeout(timer)
        timer = null
        draft.value = null
        if (current) clearDraft(current)
    }

    /** Puts the draft into the editor, at the reader's word rather than on its own. */
    function restore() {
        const found = draft.value
        if (!found) return
        content.value = found.content
        draft.value = null
    }

    watch(content, keep, {deep: true})

    onUnmounted(() => {
        if (timer) clearTimeout(timer)
    })

    return {draft, keep, forget, restore}
}

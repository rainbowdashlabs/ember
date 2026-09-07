/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, nextTick} from 'vue'
import {useRoute} from 'vue-router'

/**
 * The comment the address names, and the jump to it.
 *
 * A notification about a comment opens the page the comment hangs under and carries `?comment=<id>`
 * along, so the reader lands on the comment instead of searching a long thread for it. Call
 * `revealComment` once the comments have arrived; when the address names none, or names one the
 * reader cannot see, nothing moves and the page reads as it always does.
 */
export function useCommentHighlight() {
  const route = useRoute()

  const highlightId = computed<number | null>(() => {
    const raw = route.query.comment
    const id = Number(Array.isArray(raw) ? raw[0] : raw)
    return Number.isInteger(id) && id > 0 ? id : null
  })

  async function revealComment() {
    if (highlightId.value === null) return
    await nextTick()
    document.getElementById(`comment-${highlightId.value}`)
      ?.scrollIntoView({behavior: 'smooth', block: 'center'})
  }

  return {highlightId, revealComment}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it, vi} from 'vitest'
import {useCommentHighlight} from './useCommentHighlight'

const query: {comment?: string | string[]} = {}

vi.mock('vue-router', () => ({useRoute: () => ({query})}))

afterEach(() => {
  delete query.comment
  document.body.innerHTML = ''
})

describe('useCommentHighlight', () => {
  it('reads the comment the address names', () => {
    query.comment = '42'

    expect(useCommentHighlight().highlightId.value).toBe(42)
  })

  it('names nothing when the address carries no comment', () => {
    expect(useCommentHighlight().highlightId.value).toBeNull()
  })

  it('names nothing when the address carries something that is not a comment', () => {
    query.comment = 'gestern'

    expect(useCommentHighlight().highlightId.value).toBeNull()
  })

  it('scrolls to the comment the address names', async () => {
    query.comment = '42'
    const comment = document.createElement('div')
    comment.id = 'comment-42'
    const scrollIntoView = vi.fn()
    comment.scrollIntoView = scrollIntoView
    document.body.appendChild(comment)

    await useCommentHighlight().revealComment()

    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('leaves the page alone when the comment is not there', async () => {
    query.comment = '42'

    await expect(useCommentHighlight().revealComment()).resolves.toBeUndefined()
  })
})

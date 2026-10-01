/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import CommentSection from './CommentSection.vue'
import type {CommentSource} from '@/api/comments'
import type {CommentResponse, MemberIdentity, StationPermission} from '@/api/generated/schema'
import de from '@/i18n/de-DE'

const query: {comment?: string} = {}
const held: StationPermission[] = []

vi.mock('vue-router', () => ({useRoute: () => ({query})}))

vi.mock('@/composables/useSession', () => ({
  useSession: () => ({
    sessionInfo: ref({stationId: 'station', member: {uid: 'me'}}),
    hasPermission: (permission: StationPermission) => held.includes(permission),
  }),
}))

function writer(memberUid: string): MemberIdentity {
  return {stationUid: 'station', memberUid, name: memberUid, nameColor: null, stationName: null, displayTag: null}
}

function comment(id: number, memberUid: string, extra: Partial<CommentResponse> = {}): CommentResponse {
  return {id, author: writer(memberUid), content: `Kommentar ${id}`, deleted: false, createdAt: `2026-10-0${id}T10:00:00Z`, ...extra}
}

function source(thread: CommentResponse[], moderator: StationPermission | null): CommentSource {
  return {
    list: vi.fn().mockResolvedValue(thread),
    create: vi.fn().mockResolvedValue(undefined),
    update: vi.fn().mockResolvedValue(undefined),
    remove: vi.fn().mockResolvedValue(undefined),
    mentionables: vi.fn().mockResolvedValue({members: [], groups: []}),
    moderator,
  }
}

/**
 * The one comment section every surface uses, driven by a source that stands in for the server.
 *
 * <p>What it guards is the moderation the buttons promise: only the author is offered to change a
 * comment, and removing somebody else's is offered exactly to the holder of the surface's own manager
 * right, never to a manager of something else.
 */
describe('CommentSection', () => {
  const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})

  async function section(from: CommentSource) {
    const mounted = mount(CommentSection, {
      props: {source: from},
      global: {plugins: [i18n], stubs: {MentionInput: true, MemberName: true}},
    })
    await flushPromises()
    return mounted
  }

  function buttonsOf(mounted: Awaited<ReturnType<typeof section>>, id: number, label: string) {
    return mounted.find(`#comment-${id}`).findAll(`button[aria-label="${label}"]`)
  }

  beforeEach(() => {
    held.length = 0
    delete query.comment
  })

  it('offers the author to change and remove their own comment', async () => {
    const mounted = await section(source([comment(1, 'me')], 'NEWS_MANAGER'))

    expect(buttonsOf(mounted, 1, 'Bearbeiten')).toHaveLength(1)
    expect(buttonsOf(mounted, 1, 'Löschen')).toHaveLength(1)
  })

  it("offers nobody else anything on somebody else's comment", async () => {
    const mounted = await section(source([comment(1, 'other')], 'NEWS_MANAGER'))

    expect(buttonsOf(mounted, 1, 'Bearbeiten')).toHaveLength(0)
    expect(buttonsOf(mounted, 1, 'Löschen')).toHaveLength(0)
  })

  it("offers the surface's manager to remove, and still not to change, somebody else's comment", async () => {
    held.push('KNOWLEDGE_MANAGER')
    const from = source([comment(1, 'other')], 'KNOWLEDGE_MANAGER')
    const mounted = await section(from)

    expect(buttonsOf(mounted, 1, 'Bearbeiten')).toHaveLength(0)
    await buttonsOf(mounted, 1, 'Löschen')[0]!.trigger('click')
    await flushPromises()

    expect(from.remove).toHaveBeenCalledWith(1)
    expect(from.list).toHaveBeenCalledTimes(2)
  })

  it('offers a manager of another surface nothing', async () => {
    held.push('EVENT_MANAGER', 'STATION_ADMINISTRATOR')
    const mounted = await section(source([comment(1, 'other')], 'BOARD_MANAGER'))

    expect(buttonsOf(mounted, 1, 'Löschen')).toHaveLength(0)
  })

  it("offers nobody to remove somebody else's comment on a partner's thread", async () => {
    held.push('NEWS_MANAGER')
    const mounted = await section(source([comment(1, 'other'), comment(2, 'me')], null))

    expect(buttonsOf(mounted, 1, 'Löschen')).toHaveLength(0)
    expect(buttonsOf(mounted, 2, 'Bearbeiten')).toHaveLength(1)
    expect(buttonsOf(mounted, 2, 'Löschen')).toHaveLength(1)
  })

  it('marks a changed comment as edited and nests an answer under its comment', async () => {
    const mounted = await section(source([
      comment(1, 'other', {updatedAt: '2026-10-02T10:00:00Z'}),
      comment(2, 'me', {parentId: 1}),
    ], null))

    expect(mounted.find('#comment-1').text()).toContain('bearbeitet')
    expect(mounted.find('#comment-1 #comment-2').exists()).toBe(true)
  })

  it('marks the comment the address names', async () => {
    query.comment = '2'
    const mounted = await section(source([comment(1, 'other'), comment(2, 'other')], null))

    expect(mounted.find('#comment-2').classes()).toContain('bg-primary/10')
    expect(mounted.find('#comment-1').classes()).not.toContain('bg-primary/10')
  })

  it('hands the thread on as it was read, for a page that counts it elsewhere', async () => {
    const mounted = await section(source([comment(1, 'other')], null))

    expect(mounted.emitted('loaded')?.[0]?.[0]).toHaveLength(1)
  })
})

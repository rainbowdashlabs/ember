/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onBeforeUnmount, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberProfileCard from './MemberProfileCard.vue'
import {useFloatingPanel} from '@/composables/useFloatingPanel'
import {useDismiss} from '@/composables/useDismiss'
import {useFinePointer} from '@/composables/useFinePointer'
import {useExternalMember} from '@/composables/useExternalMember'
import {loadMemberCard} from '@/composables/useMemberCards'
import type {MemberCard} from '@/api/generated/schema'
import type {PersonIdentity} from '@/util/personIdentity'

/**
 * Opens a member's card from whatever names them: a name with its avatar, or a mention in a comment.
 *
 * <p>With a mouse the card opens after the pointer has rested on the name for a moment, and stays
 * while the pointer moves into it, so its contents can be read and its names hovered. Focus resting
 * on the button does the same for a keyboard. A tap on the button opens it on a phone, where nothing
 * hovers; that tap is kept from the row around it, so a row that opens a page still does so
 * everywhere except on the button. A press outside or escape closes it, escape returning focus to
 * the button without opening the card again.
 *
 * <p>The slot receives `toggle`, to be bound to the click of its button, and the attributes that tie
 * that button to the card. With `enabled` off the slot renders as it is and nothing opens.
 */
const props = withDefaults(defineProps<{
  identity: PersonIdentity
  name: string
  enabled?: boolean
}>(), {
  enabled: true,
})

const HOVER_OPEN_MS = 400
const HOVER_CLOSE_MS = 200

const {t} = useI18n()
const {finePointer} = useFinePointer()
const external = useExternalMember(() => props.identity)
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const {panel, style, place} = useFloatingPanel(rootRef, open)
const panelId = useId()
const card = ref<MemberCard | null>(null)
const state = ref<'loading' | 'failed' | 'ready' | 'external'>('loading')

let openTimer: ReturnType<typeof setTimeout> | undefined
let closeTimer: ReturnType<typeof setTimeout> | undefined
let returningFocus = false

function clearTimers() {
  clearTimeout(openTimer)
  clearTimeout(closeTimer)
}

async function load() {
  if (external.value) {
    state.value = 'external'
    return
  }
  const memberUid = props.identity.memberUid
  if (card.value || !memberUid) return
  state.value = 'loading'
  try {
    card.value = await loadMemberCard(memberUid)
    state.value = 'ready'
  } catch {
    state.value = 'failed'
  }
  await nextTick()
  place()
}

function show() {
  clearTimers()
  if (open.value) return
  open.value = true
  void load()
}

function close(restoreFocus: boolean) {
  clearTimers()
  if (!open.value) return
  open.value = false
  if (restoreFocus) returnFocus()
}

function returnFocus() {
  const button = rootRef.value?.querySelector<HTMLElement>('button')
  if (!button || document.activeElement === button) return
  returningFocus = true
  button.focus()
}

function approach() {
  if (!props.enabled) return
  clearTimeout(closeTimer)
  if (!open.value) openTimer = setTimeout(show, HOVER_OPEN_MS)
}

function withdraw() {
  clearTimeout(openTimer)
  if (open.value) closeTimer = setTimeout(() => close(false), HOVER_CLOSE_MS)
}

function pointerIn() {
  if (finePointer.value) approach()
}

function pointerOut() {
  if (finePointer.value) withdraw()
}

function focusIn() {
  if (returningFocus) {
    returningFocus = false
    return
  }
  approach()
}

function focusOut(event: FocusEvent) {
  const next = event.relatedTarget as Node | null
  if (next && (rootRef.value?.contains(next) || panel.value?.contains(next))) return
  withdraw()
}

function toggle(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (open.value) close(false)
  else show()
}

useDismiss(open, () => [rootRef.value, panel.value], by => close(by === 'escape'))
onBeforeUnmount(clearTimers)
</script>

<template>
  <span
      ref="rootRef"
      role="presentation"
      @focusin="focusIn"
      @focusout="focusOut"
      @mouseenter="pointerIn"
      @mouseleave="pointerOut"
  >
    <slot
        :toggle="toggle"
        :trigger-attrs="{
          'aria-controls': panelId,
          'aria-expanded': open,
          'aria-haspopup': 'dialog',
          'aria-label': t('memberCard.open', {name}),
        }"
    />
    <Teleport v-if="enabled" to="body">
      <div
          v-if="open"
          ref="panel"
          :style="style"
          class="z-50"
          role="presentation"
          @focusin="approach"
          @focusout="focusOut"
          @mouseenter="pointerIn"
          @mouseleave="pointerOut"
      >
        <div
            :id="panelId"
            :aria-label="t('memberCard.label', {name})"
            class="rounded-theme border border-(--border) bg-(--bg) text-left shadow-lg"
            role="dialog"
            tabindex="-1"
        >
          <MemberProfileCard :card="card" :identity="identity" :name="name" :state="state"/>
        </div>
      </div>
    </Teleport>
  </span>
</template>

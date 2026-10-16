/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import MentionInput from '@/components/comment/MentionInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {ConversationMessageResponse, MemberCompletion} from '@/api/generated/schema'
import {useMessageWords} from '@/composables/useMessageWords'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The box at the bottom of a conversation. Enter sends and Shift+Enter starts a new line; in an
 * empty box the up arrow changes the reader's own last message, and Escape stops answering or
 * changing. In a group the people in it can be mentioned with the at sign.
 */
const props = defineProps<{
  placeholder: string
  mentionables: MemberCompletion[]
  replyTo: ConversationMessageResponse | null
  editing: ConversationMessageResponse | null
  send: (text: string) => Promise<void>
  save: (message: ConversationMessageResponse, text: string) => Promise<void>
}>()

const emit = defineEmits<{
  cancel: []
  editLast: []
}>()

const {t} = useI18n()
const {nameOf} = useMessageWords()
const text = ref('')
const busy = ref(false)
const failure = ref<Failure | null>(null)
const box = ref<HTMLElement | null>(null)
const generation = ref(0)

/**
 * Puts a text into the box and the cursor back into it. The mention input leaves a focused editor
 * alone when its value changes, so the box is drawn anew rather than told.
 */
async function refill(value: string) {
  text.value = value
  generation.value++
  await nextTick()
  box.value?.querySelector<HTMLElement>('[contenteditable]')?.focus()
}

watch(() => props.editing, message => { void refill(message ? message.content : '') })
watch(() => props.replyTo, message => {
  if (message) box.value?.querySelector<HTMLElement>('[contenteditable]')?.focus()
})

async function submit() {
  const written = text.value.trim()
  if (!written || busy.value) return
  busy.value = true
  failure.value = null
  try {
    if (props.editing) await props.save(props.editing, written)
    else await props.send(written)
    await refill('')
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.defaultPrevented || event.isComposing) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void submit()
  } else if (event.key === 'ArrowUp' && !text.value && !props.editing) {
    event.preventDefault()
    emit('editLast')
  } else if (event.key === 'Escape' && (props.editing || props.replyTo)) {
    event.preventDefault()
    emit('cancel')
  }
}
</script>

<template>
  <div class="border-t border-(--border) p-3 space-y-2">
    <div v-if="replyTo || editing" class="flex items-center gap-2">
      <MutedText size="sm" class="min-w-0 flex-1 truncate">
        {{ editing ? t('messages.composer.editing') : t('messages.composer.replyingTo', {name: nameOf(replyTo?.author)}) }}
      </MutedText>
      <IconButton :icon="['fas', 'xmark']" :label="t('messages.composer.cancel')" @click="$emit('cancel')"/>
    </div>
    <FailureAlert :failure="failure"/>
    <div ref="box" class="flex items-end gap-2" data-testid="message-composer" role="presentation" @keydown="onKeydown">
      <MentionInput :key="generation" v-model="text" :members="mentionables" :placeholder="placeholder"
                    class="min-w-0 flex-1"/>
      <IconButton :icon="['fas', 'paper-plane']" :label="t('messages.composer.send')" :disabled="busy || !text.trim()"
                  data-testid="message-send" class="text-primary" @click="submit"/>
    </div>
    <MutedText tag="p" class="hidden sm:block">{{ t('messages.composer.hint') }}</MutedText>
  </div>
</template>

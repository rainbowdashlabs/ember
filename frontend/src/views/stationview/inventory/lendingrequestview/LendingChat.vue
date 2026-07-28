/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {LendingStatus, type EnrichedMessage, type LendingRequestDetail} from '@/api/lending'
import {formatDateTime} from '@/util/format'

const newMessage = defineModel<string>('newMessage', {required: true})

const props = defineProps<{
  detail: LendingRequestDetail
  messages: EnrichedMessage[]
  sending: boolean
}>()

const emit = defineEmits<{
  send: []
}>()

const {t} = useI18n()
const chatContainer = ref<HTMLElement | null>(null)

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

watch(() => props.messages, async () => {
  await nextTick()
  scrollToBottom()
}, {deep: true})

onMounted(async () => {
  await nextTick()
  scrollToBottom()
})

defineExpose({scrollToBottom})
</script>

<template>
  <SubHeader class="mb-2">{{ t('lending.chat') }}</SubHeader>
  <NeutralContainer class="mb-2">
    <div ref="chatContainer" class="max-h-80 overflow-y-auto flex flex-col gap-2 p-2">
      <div v-if="messages.length === 0" class="text-sm text-[var(--text-muted)] text-center py-4">
        {{ t('lending.noMessages') }}
      </div>
      <div v-for="msg in messages" :key="msg.message.id"
           :class="msg.message.isSystem ? 'text-center text-xs text-[var(--text-muted)] italic py-1' : 'flex flex-col gap-0.5'">
        <template v-if="msg.message.isSystem">
          <span>{{ msg.message.message }} - {{ formatDateTime(msg.message.createdAt) }}</span>
        </template>
        <template v-else>
          <div class="flex items-center gap-2">
            <span v-if="msg.senderName" class="text-xs font-medium">{{ msg.senderName }} <span class="text-[var(--text-muted)]">({{ msg.senderStationName }})</span></span>
            <span v-else class="text-xs font-medium">{{ msg.senderStationName }}</span>
            <span class="text-xs text-[var(--text-muted)]">{{ formatDateTime(msg.message.createdAt) }}</span>
          </div>
          <div class="bg-[var(--bg)] rounded px-3 py-1.5 text-sm">{{ msg.message.message }}</div>
        </template>
      </div>
    </div>
  </NeutralContainer>

  <div v-if="detail.request.request.status !== LendingStatus.CLOSED && detail.request.request.status !== LendingStatus.DECLINED" class="flex gap-2">
    <TextInput v-model="newMessage" :placeholder="t('lending.messagePlaceholder')" class="flex-1" @keyup.enter="emit('send')"/>
    <PrimaryButton :disabled="sending || !newMessage.trim()" @click="emit('send')">
      <font-awesome-icon :icon="['fas', 'paper-plane']"/>
    </PrimaryButton>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {MessagePersonResponse} from '@/api/generated/schema'
import {getBlocks, unblock} from '@/api/messages'
import {useMessagesStatus} from '@/composables/useMessagesStatus'
import {personName} from '@/util/conversationTitle'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The reader's message settings: whether they are mailed about messages left unread, and the
 * people they blocked, each of whom can be let back in.
 */
const open = defineModel<boolean>({required: true})

const {t} = useI18n()
const {status, setMail} = useMessagesStatus()

const blocked = ref<MessagePersonResponse[]>([])
const failure = ref<Failure | null>(null)

watch(open, async isOpen => {
  if (!isOpen) return
  failure.value = null
  blocked.value = await getBlocks().catch(() => [])
})

const mail = computed({
  get: () => status.value.mail,
  set: (wanted: boolean) => {
    setMail(wanted).catch(e => { failure.value = describeFailure(e, t) })
  },
})

async function letBackIn(person: MessagePersonResponse) {
  try {
    await unblock(person.uid)
    blocked.value = blocked.value.filter(entry => entry.uid !== person.uid)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('messages.settings.title') }}</SubHeader>
      <ToggleSetting v-model="mail" :label="t('messages.settings.mail')" :hint="t('messages.settings.mailHint')"/>
      <FieldLabel>{{ t('messages.settings.blocked') }}</FieldLabel>
      <EmptyState v-if="blocked.length === 0" compact :message="t('messages.settings.noneBlocked')"/>
      <ul class="divide-y divide-(--border)">
        <li v-for="person in blocked" :key="person.uid" class="flex items-center gap-3 py-2">
          <UserAvatar :identity="{accountUid: person.uid}" :name="personName(person)" size="sm"/>
          <span class="min-w-0 flex-1 truncate text-sm">{{ personName(person) }}</span>
          <SecondaryButton compact @click="letBackIn(person)">{{ t('messages.unblock') }}</SecondaryButton>
        </li>
      </ul>
      <FailureAlert :failure="failure"/>
    </div>
  </Modal>
</template>

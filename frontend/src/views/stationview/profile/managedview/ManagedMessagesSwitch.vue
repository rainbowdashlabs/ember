/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {managedMembers} from '@/api'
import type {ManagedAccess} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Whether a member in the guardian's care may use messages between people. It stays off until a
 * guardian turns it on; turning it off again hides their conversations and stops new messages.
 */
const props = defineProps<{
  memberId: number
  access: ManagedAccess
}>()

const emit = defineEmits<{
  changed: [access: ManagedAccess]
}>()

const {t} = useI18n()
const failure = ref<Failure | null>(null)

async function toggle(allowed: boolean) {
  failure.value = null
  try {
    emit('changed', await managedMembers.setMessages(props.memberId, allowed))
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <div class="space-y-2 border-t border-(--border) pt-4">
    <div class="flex items-start justify-between gap-4">
      <div>
        <FieldLabel>{{ t('profileManaged.access.messages') }}</FieldLabel>
        <MutedText tag="p" size="sm">{{ t('profileManaged.access.messagesHint') }}</MutedText>
      </div>
      <ToggleInput data-testid="managed-messages-toggle" :model-value="access.messagesAllowed"
                   :aria-label="t('profileManaged.access.messages')" @update:model-value="toggle"/>
    </div>
    <FailureAlert :failure="failure"/>
  </div>
</template>

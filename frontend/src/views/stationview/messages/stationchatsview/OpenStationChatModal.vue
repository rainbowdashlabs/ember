/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {MemberGroup, StationChatResponse, UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {openStationChat, updateStationChat} from '@/api/messages'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Opens a station chat, or changes one: its name, whom it is for and whether the guardians of its
 * people are in it too. Whom it is for is picked the way an appointment picks its audience, so a
 * group together with its leaders, or several tags, make one chat; picking nobody means the whole
 * station without trial members. A chat is edited here as a whole, also while it is closed, which is
 * how one whose audience is gone gets a new one. Such a chat shows only what is left of its audience,
 * so the dialog asks for it to be checked before saving makes it whole again. The audience and the guardian choice always go
 * along, an empty audience included, because the server refuses a change that leaves them out.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  /** The chat to change, or nothing to open a new one. */
  chat?: StationChatResponse | null
  groups: MemberGroup[]
  tags: UserTag[]
  onSaved: () => Promise<void> | void
}>()

const {t} = useI18n()
const name = ref('')
const audience = ref<RestrictionSelection>(emptyRestriction())
const includeGuardians = ref(false)

watch(open, isOpen => {
  if (!isOpen) return
  clearError()
  name.value = props.chat?.name ?? ''
  audience.value = props.chat ? toRestriction(props.chat.audience) : emptyRestriction()
  includeGuardians.value = props.chat?.includeGuardians ?? false
}, {immediate: true})

const {running: saving, failure, run: submit, clearError} = useAsyncAction(async () => {
  const request = {name: name.value.trim(), audience: audience.value, includeGuardians: includeGuardians.value}
  if (props.chat) await updateStationChat(props.chat.uid, request)
  else await openStationChat(request)
  open.value = false
  await props.onSaved()
})
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3 p-4">
      <SubHeader>{{ chat ? t('stationChats.open.editTitle') : t('stationChats.open.title') }}</SubHeader>
      <FieldLabel for="station-chat-name">{{ t('stationChats.open.name') }}</FieldLabel>
      <TextInput id="station-chat-name" v-model="name" maxlength="100" data-testid="station-chat-name"/>
      <FieldLabel>{{ t('stationChats.open.audience') }}</FieldLabel>
      <Alert v-if="chat?.audienceGone" variant="info" data-testid="station-chat-audience-gone-check">
        {{ t('stationChats.open.audienceGoneCheck') }}
      </Alert>
      <RestrictionsField v-model="audience" :groups="groups" :tags="tags" data-testid="station-chat-audience"/>
      <FieldHint>{{ t('stationChats.open.audienceHint') }}</FieldHint>
      <ToggleSetting v-model="includeGuardians" :label="t('stationChats.guardians')" :hint="t('stationChats.guardiansHint')"/>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('messages.composer.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !name.trim()" :icon="chat ? ['fas', 'floppy-disk'] : ['fas', 'plus']"
                       data-testid="station-chat-submit" @click="submit">
          {{ chat ? t('stationChats.open.save') : t('stationChats.open.submit') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>

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
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {ChatAudience, type MemberGroup, type UserTag} from '@/api/generated/schema'
import {openStationChat} from '@/api/messages'
import {listGroups} from '@/api/memberGroups'
import {listChoosableTags} from '@/api/userTags'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Opens a chat for the whole station, a group or a tag. A group or tag chat can take the guardians
 * of its people in as well. Private tags are not offered: a chat shows who is in it.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  onOpened: () => Promise<void> | void
}>()

const {t} = useI18n()
const name = ref('')
const audience = ref<ChatAudience>(ChatAudience.STATION)
const chosen = ref<number | null>(null)
const includeGuardians = ref(false)
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const saving = ref(false)
const failure = ref<Failure | null>(null)

/** The audiences in the order the dialog offers them. */
const AUDIENCES = [ChatAudience.STATION, ChatAudience.GROUP, ChatAudience.TAG] as const

const choices = computed(() => audience.value === ChatAudience.GROUP ? groups.value : tags.value)

watch(open, async isOpen => {
  if (!isOpen) return
  name.value = ''
  audience.value = ChatAudience.STATION
  chosen.value = null
  includeGuardians.value = false
  failure.value = null
  groups.value = await listGroups().catch(() => [])
  tags.value = await listChoosableTags().catch(() => [])
})

watch(audience, () => {
  chosen.value = null
  includeGuardians.value = false
})

function chooseAudience(value: string | number | null | undefined) {
  if (value === ChatAudience.GROUP || value === ChatAudience.TAG) {
    audience.value = value
    return
  }
  audience.value = ChatAudience.STATION
}

async function submit() {
  saving.value = true
  failure.value = null
  try {
    await openStationChat({
      name: name.value.trim(),
      audience: audience.value,
      groupId: audience.value === ChatAudience.GROUP ? chosen.value : null,
      tagId: audience.value === ChatAudience.TAG ? chosen.value : null,
      includeGuardians: audience.value !== ChatAudience.STATION && includeGuardians.value,
    })
    open.value = false
    await props.onOpened()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3 p-4">
      <SubHeader>{{ t('stationChats.open.title') }}</SubHeader>
      <FieldLabel for="station-chat-name">{{ t('stationChats.open.name') }}</FieldLabel>
      <TextInput id="station-chat-name" v-model="name" maxlength="100" data-testid="station-chat-name"/>
      <FieldLabel for="station-chat-audience">{{ t('stationChats.open.audience') }}</FieldLabel>
      <SelectInput id="station-chat-audience" :model-value="audience" data-testid="station-chat-audience"
                   @update:model-value="chooseAudience">
        <option v-for="choice in AUDIENCES" :key="choice" :value="choice">{{ t(`stationChats.audience.${choice}`) }}</option>
      </SelectInput>
      <template v-if="audience !== ChatAudience.STATION">
        <FieldLabel for="station-chat-target">{{ t(`stationChats.open.which.${audience}`) }}</FieldLabel>
        <SelectInput id="station-chat-target" v-model="chosen" data-testid="station-chat-target">
          <option :value="null" disabled>{{ t('stationChats.choose') }}</option>
          <option v-for="choice in choices" :key="choice.id" :value="choice.id">{{ choice.name }}</option>
        </SelectInput>
        <ToggleSetting v-model="includeGuardians" :label="t('stationChats.guardians')"
                       :hint="t('stationChats.guardiansHint')"/>
      </template>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('messages.composer.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !name.trim() || (audience !== ChatAudience.STATION && chosen === null)"
                       :icon="['fas', 'plus']" data-testid="station-chat-submit" @click="submit">
          {{ t('stationChats.open.submit') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>

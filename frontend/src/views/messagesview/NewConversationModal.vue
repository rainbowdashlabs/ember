/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {ReachablePersonResponse} from '@/api/generated/schema'
import {openDirect, startGroup} from '@/api/messages'
import {describeFailure, type Failure} from '@/util/failure'
import PersonPicker from './PersonPicker.vue'

/**
 * Starts writing to somebody: one person opens the direct conversation with them, the one that
 * exists already where there is one; several start a group, which may be given a name.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  onStarted: () => Promise<void> | void
}>()

const {t} = useI18n()
const router = useRouter()

const chosen = ref<ReachablePersonResponse[]>([])
const name = ref('')
const busy = ref(false)
const failure = ref<Failure | null>(null)

const group = computed(() => chosen.value.length > 1)

watch(open, isOpen => {
  if (!isOpen) return
  chosen.value = []
  name.value = ''
  failure.value = null
})

async function start() {
  busy.value = true
  failure.value = null
  try {
    const people = chosen.value.map(person => person.uid)
    const started = group.value ? await startGroup(people, name.value.trim() || null) : await openDirect(people[0] ?? '')
    open.value = false
    await props.onStarted()
    await router.push(`/messages/${started.uid}`)
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('messages.newConversation.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('messages.newConversation.hint') }}</MutedText>
      <PersonPicker v-model="chosen"/>
      <TextInput v-if="group" v-model="name" :placeholder="t('messages.newConversation.groupName')"/>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="busy || chosen.length === 0" data-testid="messages-start" @click="start">
          {{ group ? t('messages.newConversation.startGroup') : t('messages.newConversation.start') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>

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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import {procedures} from '@/api'
import type {ProcedureTemplate} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {dateToInstant} from '@/util/format'

/**
 * Starts a procedure from a template, asking only whom it is about and when it is due. Everything else
 * comes from the template: its steps and their order, its description and its managing party. It starts
 * the way procedures from sign-ups do, for one person, so the server names it after the template and them.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  template: ProcedureTemplate
  /** The members a procedure can be about. */
  members: MemberOption[]
}>()

const {t} = useI18n()
const router = useRouter()

/** The member it is about, as the picker holds it: the id, or empty while nobody is chosen. */
const about = ref('')
const dueAt = ref('')

/**
 * A preview of the name the procedure gets: the template's, followed by the person's. The server names
 * it, so this only says ahead what it will write.
 */
const name = computed(() => {
  const person = props.members.find(member => member.value === about.value)?.name
  return person ? `${props.template.name}: ${person}` : props.template.name
})

const {running, failure, run, clearError} = useAsyncAction(
    async () => {
      const [created] = await procedures.createProceduresForMembers({
        templateId: props.template.id,
        memberIds: [Number(about.value)],
        name: props.template.name,
        description: props.template.description || undefined,
        dueAt: dateToInstant(dueAt.value) ?? undefined,
        isPublic: true,
      })
      open.value = false
      if (created) await router.push({name: 'procedure-detail', params: {id: created.id}})
    },
)

watch(open, opened => {
  if (!opened) return
  about.value = ''
  dueAt.value = ''
  clearError()
})
</script>

<template>
  <Modal v-model="open">
    <form class="space-y-4" data-testid="procedure-from-template" @submit.prevent="about && run()">
      <SubHeader>{{ template.name }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('procedures.start.dialogHint') }}</MutedText>
      <div>
        <FieldLabel class="mb-1">{{ t('procedures.about') }}</FieldLabel>
        <MemberSelectInput v-model="about" :members="members" data-testid="procedure-from-template-about"/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('procedures.dueDate') }}</FieldLabel>
        <DateInput v-model="dueAt" data-testid="procedure-from-template-due"/>
      </div>
      <MutedText v-if="about" tag="p" size="sm" data-testid="procedure-from-template-name">
        {{ t('procedures.start.named', {name}) }}
      </MutedText>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton type="submit" :disabled="!about || running" data-testid="procedure-from-template-submit">
          {{ t('procedures.start.create') }}
        </PrimaryButton>
      </ButtonRow>
    </form>
  </Modal>
</template>

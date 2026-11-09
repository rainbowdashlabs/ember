/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProcedureFromTemplateModal from './ProcedureFromTemplateModal.vue'
import type {MemberCompletion, ProcedureTemplate} from '@/api/generated/schema'

/**
 * How a procedure starts: from one of the station's templates, which brings everything but whom it is
 * about and when it is due, or empty, in the form where every part is written by hand.
 */
defineProps<{
  /** The station's templates, without the archived ones. */
  templates: ProcedureTemplate[]
  /** The members a procedure can be about. */
  members: MemberCompletion[]
}>()

const emit = defineEmits<{
  blank: []
}>()

const {t} = useI18n()

/** The template whose dialog is open, or nothing while none is. */
const chosen = ref<ProcedureTemplate | null>(null)
const open = ref(false)

function choose(template: ProcedureTemplate) {
  chosen.value = template
  open.value = true
}
</script>

<template>
  <div class="space-y-6">
    <div class="space-y-3">
      <SubHeader>{{ t('procedures.start.fromTemplate') }}</SubHeader>
      <MutedText v-if="templates.length === 0" tag="div" size="sm" class="py-2">
        {{ t('procedures.start.noTemplates') }}
      </MutedText>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <NeutralContainer v-for="template in templates" :key="template.id"
                          :data-testid="`procedure-start-template-${template.id}`"
                          class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                          @click="choose(template)">
          <div class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <span class="font-medium">{{ template.name }}</span>
              <PrimaryButton :icon="['fas', 'plus']">{{ t('procedures.start.create') }}</PrimaryButton>
            </div>
            <MutedText v-if="template.description" tag="p" size="sm">{{ template.description }}</MutedText>
          </div>
        </NeutralContainer>
      </div>
    </div>

    <div class="space-y-3">
      <SubHeader>{{ t('procedures.start.withoutTemplate') }}</SubHeader>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                          data-testid="procedure-start-blank" @click="emit('blank')">
          <div class="space-y-2">
            <div class="flex items-center justify-between gap-2">
              <span class="font-medium">{{ t('procedures.start.blankTitle') }}</span>
              <PrimaryButton :icon="['fas', 'plus']">{{ t('procedures.start.create') }}</PrimaryButton>
            </div>
            <MutedText tag="p" size="sm">{{ t('procedures.start.blankHint') }}</MutedText>
          </div>
        </NeutralContainer>
      </div>
    </div>

    <ProcedureFromTemplateModal v-if="chosen" v-model="open" :template="chosen" :members="members"/>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import StartTile from '@/components/start/StartTile.vue'
import StartTileSection from '@/components/start/StartTileSection.vue'
import ProcedureFromTemplateModal from './ProcedureFromTemplateModal.vue'
import type {ProcedureTemplate} from '@/api/generated/schema'
import type {MemberOption} from '@/components/input/select/memberOption'

/**
 * How a procedure starts: from one of the station's templates, which brings everything but whom it is
 * about and when it is due, or empty, in the form where every part is written by hand.
 */
defineProps<{
  /** The station's templates, without the archived ones. */
  templates: ProcedureTemplate[]
  /** The members a procedure can be about. */
  members: MemberOption[]
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
    <StartTileSection :title="t('procedures.start.fromTemplate')"
                      :empty-note="templates.length === 0 ? t('procedures.start.noTemplates') : null">
      <StartTile v-for="template in templates" :key="template.id"
                 :data-testid="`procedure-start-template-${template.id}`"
                 :title="template.name" :button-label="t('procedures.start.create')" :hint="template.description"
                 @start="choose(template)"/>
    </StartTileSection>

    <StartTileSection :title="t('procedures.start.withoutTemplate')">
      <StartTile data-testid="procedure-start-blank" :title="t('procedures.start.blankTitle')"
                 :button-label="t('procedures.start.create')" :hint="t('procedures.start.blankHint')"
                 @start="emit('blank')"/>
    </StartTileSection>

    <ProcedureFromTemplateModal v-if="chosen" v-model="open" :template="chosen" :members="members"/>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import {FormFieldKind, type FormField, type Placeholder} from '@/api/generated/schema'
import PlaceholderTextInput from './placeholderpicker/PlaceholderTextInput.vue'

/**
 * What one form field of the PDF is filled with: a text with placeholders, or for a check box the
 * value whose yes ticks it. Left empty, the field keeps what the PDF shows.
 *
 * <p>A PDF often names its fields {@code Text1} and so on, so the entry also says what the PDF's
 * author called the field, which page it is on and what it holds as uploaded. Chosen on the page, the
 * entry is marked and scrolled into view; working in it chooses its field on the page.
 */
const text = defineModel<string>({required: true})

const props = defineProps<{
  field: FormField
  placeholders: Placeholder[]
  legal: boolean
  chosen: boolean
}>()

const emit = defineEmits<{
  choose: []
}>()

const {t} = useI18n()
const root = ref<HTMLElement | null>(null)

const label = computed(() => props.field.tooltip ? `${props.field.tooltip} (${props.field.name})` : props.field.name)

const whereAndWhat = computed(() => [
  props.field.rect ? t('documentTemplates.formFieldPage', {page: props.field.rect.page}) : null,
  props.field.value ? t('documentTemplates.formFieldValue', {value: props.field.value}) : null,
].filter(part => part !== null).join(' · '))

watch(() => props.chosen, async chosen => {
  if (!chosen) return
  await nextTick()
  root.value?.scrollIntoView({block: 'nearest', behavior: 'smooth'})
})
</script>

<template>
  <div ref="root" :class="chosen ? 'ring-2 ring-primary' : ''" class="rounded-theme p-1" data-testid="form-binding"
       @focusin="emit('choose')">
    <LabelledField :label="label"
                   :help="t(field.kind === FormFieldKind.CHECK ? 'documentTemplates.checkWhenHelp' : 'documentTemplates.formFieldHelp')">
      <PlaceholderTextInput v-model="text" :placeholders="placeholders" :legal="legal" :prompt="t('documentTemplates.formFieldKeeps')"/>
    </LabelledField>
    <p v-if="whereAndWhat" class="px-1 text-xs text-(--text-muted)" data-testid="form-binding-where">{{ whereAndWhat }}</p>
  </div>
</template>

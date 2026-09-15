/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {FieldWidths, type FieldWidthName} from '@/components/profilefields/fieldLayout'
import {widthLabel} from '../fieldTypes'
import type {AssignmentRequest, ProfileField} from '@/api/profileFields'
import type {Audience} from '@/composables/useFieldsConfig'

/**
 * One audience the question is put to.
 *
 * <p>Width and whether an answer is expected are the question's unless this audience says otherwise,
 * so both are offered as a choice with the question's own answer standing first and named for what it
 * is. Picking it writes nothing down, which is what lets the question's answer keep changing under it.
 *
 * <p>Who may write the answer works the same way: the question carries the default and this row may
 * say otherwise for its own audience, which is how a team writes a date the members only read.
 */
const props = defineProps<{
  field: ProfileField
  audience: Audience
}>()

const emit = defineEmits<{
  (e: 'remove'): void
  (e: 'set', patch: Partial<AssignmentRequest>): void
}>()

const {t} = useI18n()

const widths: FieldWidthName[] = [FieldWidths.FULL, FieldWidths.HALF, FieldWidths.THIRD]

/** The question's own width, named so the entry that follows it reads as what it falls back to. */
const inheritedWidth = computed(() => t('membersConfig.audiences.inherit', {
    value: widthLabel(t, (props.field.width ?? FieldWidths.FULL) as FieldWidthName),
}))

const inheritedRequired = computed(() => t('membersConfig.audiences.inherit', {
    value: props.field.required ? t('common.yes') : t('common.no'),
}))

const inheritedReadonly = computed(() => t('membersConfig.audiences.inherit', {
    value: props.field.readonly ? t('common.yes') : t('common.no'),
}))

const requiredValue = computed(() => {
    const override = props.audience.assignment.requiredOverride
    return override === null || override === undefined ? '' : String(override)
})

const readonlyValue = computed(() => {
    const override = props.audience.assignment.readonlyOverride
    return override === null || override === undefined ? '' : String(override)
})

function onWidth(value: string) {
    emit('set', {widthOverride: value === '' ? null : value})
}

function onRequired(value: string) {
    emit('set', {requiredOverride: value === '' ? null : value === 'true'})
}

function onReadonly(value: string) {
    emit('set', {readonlyOverride: value === '' ? null : value === 'true'})
}
</script>

<template>
  <div
      :data-testid="`audience-${audience.label}`"
      class="flex flex-wrap items-center gap-3 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 py-2 text-sm">
    <span class="font-medium min-w-32">{{ audience.label }}</span>

    <label class="flex flex-col gap-0.5 text-xs">
      <span class="text-(--text-muted)">{{ t('membersConfig.colWidth') }}</span>
      <SelectInput :model-value="audience.assignment.widthOverride ?? ''" class="min-w-44"
                   @update:model-value="onWidth(String($event))">
        <option value="">{{ inheritedWidth }}</option>
        <option v-for="width in widths" :key="width" :value="width">{{ widthLabel(t, width) }}</option>
      </SelectInput>
    </label>

    <label class="flex flex-col gap-0.5 text-xs">
      <span class="text-(--text-muted)">{{ t('membersConfig.fieldRequired') }}</span>
      <SelectInput :model-value="requiredValue" class="min-w-44" @update:model-value="onRequired(String($event))">
        <option value="">{{ inheritedRequired }}</option>
        <option value="true">{{ t('common.yes') }}</option>
        <option value="false">{{ t('common.no') }}</option>
      </SelectInput>
    </label>

    <label class="flex flex-col gap-0.5 text-xs">
      <span class="text-(--text-muted)">{{ t('membersConfig.fieldReadonly') }}</span>
      <SelectInput :model-value="readonlyValue" class="min-w-44" @update:model-value="onReadonly(String($event))">
        <option value="">{{ inheritedReadonly }}</option>
        <option value="true">{{ t('common.yes') }}</option>
        <option value="false">{{ t('common.no') }}</option>
      </SelectInput>
    </label>

    <DeleteButton class="ml-auto self-end" @click="emit('remove')"/>
  </div>
</template>

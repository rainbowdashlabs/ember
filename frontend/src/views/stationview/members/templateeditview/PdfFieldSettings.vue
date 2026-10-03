/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SignerSelect from '@/components/documents/SignerSelect.vue'
import {PdfFieldKind, TextAlign, type PdfField, type Placeholder} from '@/api/generated/schema'
import PlaceholderPopover from './placeholderpicker/PlaceholderPopover.vue'
import {DEFAULT_FONT_SIZE} from './pdfFields'
import {withPlaceholder} from './placeholderText'

/**
 * What the chosen field does: the text it prints and how, the value whose yes ticks it, or who signs
 * in it. Where it sits is set on the page itself.
 */
const field = defineModel<PdfField>({required: true})

defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const aligns = [TextAlign.LEFT, TextAlign.CENTER, TextAlign.RIGHT]

function setText(text: string | undefined) {
  field.value = {...field.value, text: text ?? ''}
}

function append(placeholder: Placeholder) {
  setText(withPlaceholder(field.value.text ?? '', placeholder.key))
}
</script>

<template>
  <div class="space-y-3 rounded-lg border border-(--border) p-3" data-testid="pdf-field-settings">
    <div class="flex items-center justify-between gap-2">
      <SecondaryBadge>{{ t(`documentTemplates.fieldKind.${field.kind}`) }}</SecondaryBadge>
      <DeleteButton @click="emit('remove')"/>
    </div>
    <SignerSelect v-if="field.kind === PdfFieldKind.SIGNATURE" :model-value="field.role"
                  @update:model-value="role => field = {...field, role: role ?? null}"/>
    <template v-else>
      <LabelledField :label="t(field.kind === PdfFieldKind.CHECK ? 'documentTemplates.checkWhen' : 'documentTemplates.fieldText')"
                     :help="t(field.kind === PdfFieldKind.CHECK ? 'documentTemplates.checkWhenHelp' : 'documentTemplates.fieldTextHelp')">
        <TextInput :model-value="field.text ?? ''" data-testid="pdf-field-text" @update:model-value="setText"/>
      </LabelledField>
      <PlaceholderPopover :placeholders="placeholders" :legal="legal" @pick="append"/>
    </template>
    <template v-if="field.kind === PdfFieldKind.TEXT">
      <div class="grid gap-3 sm:grid-cols-2">
        <LabelledField :label="t('documentTemplates.fontSize')">
          <NumberInput :model-value="field.fontSize"
                       @update:model-value="size => field = {...field, fontSize: size ?? DEFAULT_FONT_SIZE}"/>
        </LabelledField>
        <LabelledField :label="t('documentTemplates.alignLabel')">
          <SelectInput :model-value="field.align" @update:model-value="align => field = {...field, align: align as TextAlign}">
            <option v-for="align in aligns" :key="align" :value="align">{{ t(`documentTemplates.align.${align}`) }}</option>
          </SelectInput>
        </LabelledField>
      </div>
      <ToggleSetting :model-value="field.wrap" :label="t('documentTemplates.wrap')" :hint="t('documentTemplates.wrapHint')"
                     @update:model-value="wrap => field = {...field, wrap}"/>
    </template>
  </div>
</template>

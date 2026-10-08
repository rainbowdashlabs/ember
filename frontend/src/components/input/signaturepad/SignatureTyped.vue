/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'

/**
 * A signature typed rather than drawn, for whoever cannot draw on the screen: the name as typed, set in a
 * handwriting style and turned into the same kind of picture a drawn one is.
 *
 * <p>Works with the keyboard alone. The picture follows every change of the name and is shown under the
 * field, with the name as its text alternative. A name is at most {@link MAX_LENGTH} characters, which
 * keeps the picture within the size a signature is taken at.
 */
const emit = defineEmits<{change: [dataUrl: string | null]}>()

const {t} = useI18n()

const FONT_SIZE = 64
const PADDING = 24
const FONT = `italic ${FONT_SIZE}px "Segoe Script", "Brush Script MT", "Snell Roundhand", "URW Chancery L", cursive`

const MAX_LENGTH = 80

const name = ref('')
const typedName = computed(() => name.value.slice(0, MAX_LENGTH).trim())
const preview = ref<string | null>(null)

/** The name drawn onto a sheet just large enough for it, as a PNG, or null where it cannot be drawn. */
function picture(text: string): string | null {
  const sheet = document.createElement('canvas')
  const measure = sheet.getContext('2d')
  if (!measure) return null
  measure.font = FONT
  sheet.width = Math.ceil(measure.measureText(text).width) + 2 * PADDING
  sheet.height = FONT_SIZE * 2
  const pen = sheet.getContext('2d')
  if (!pen) return null
  pen.font = FONT
  pen.fillStyle = '#111111'
  pen.textBaseline = 'middle'
  pen.fillText(text, PADDING, FONT_SIZE)
  return sheet.toDataURL('image/png')
}

watch(typedName, (text) => {
  preview.value = text ? picture(text) : null
  emit('change', preview.value)
})
</script>

<template>
  <div class="space-y-3">
    <LabelledField :label="t('signaturePad.typeLabel')" :help="t('signaturePad.typeHint')">
      <TextInput v-model="name" autocomplete="name" :maxlength="MAX_LENGTH" data-testid="signature-typed-name"/>
    </LabelledField>
    <img
        v-if="preview"
        :src="preview"
        :alt="t('signaturePad.typedPreview', {name: typedName})"
        class="block max-w-xl max-h-32 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-white"
    />
  </div>
</template>

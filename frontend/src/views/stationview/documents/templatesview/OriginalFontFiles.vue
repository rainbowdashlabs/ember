/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The font files the original of a Word form is drawn with, for its preview only: one for each font the
 * form names but does not carry itself. The file stands for that font whatever name it gives itself, so a
 * Frutiger file that calls itself Frutiger LT Std still draws Frutiger 45 Light. The files are sent with
 * each preview and never kept.
 */
const files = defineModel<Record<string, File>>({required: true})

const props = defineProps<{
  missing: readonly string[]
  embedded: readonly string[]
}>()

const {t} = useI18n()
const picker = ref<HTMLInputElement | null>(null)
const target = ref<string | null>(null)

const without = computed(() => props.missing.filter(family => !props.embedded.includes(family)))

function pick(family: string) {
  target.value = family
  picker.value?.click()
}

function added(event: Event) {
  const input = event.target as HTMLInputElement
  const [file] = Array.from(input.files ?? [])
  input.value = ''
  if (file && target.value) files.value = {...files.value, [target.value]: file}
}

function remove(family: string) {
  files.value = Object.fromEntries(Object.entries(files.value).filter(([named]) => named !== family))
}
</script>

<template>
  <div class="space-y-2" data-testid="form-original-fonts">
    <MutedText v-for="family in embedded" :key="`embedded-${family}`" tag="p" size="sm">
      {{ t('documentTemplates.formImport.originalEmbedded', {family}) }}
    </MutedText>
    <div v-for="family in without" :key="family" class="flex flex-wrap items-center gap-2 text-sm"
         :data-testid="`form-original-font-${family}`">
      <span>{{ family }}:</span>
      <template v-if="files[family]">
        <span class="truncate">{{ files[family]?.name }}</span>
        <IconButton :icon="['fas', 'xmark']" :label="t('documentTemplates.formImport.originalRemove', {name: files[family]?.name})"
                    @click="remove(family)"/>
      </template>
      <SecondaryButton v-else :icon="['fas', 'upload']" :data-testid="`form-original-font-${family}-add`"
                       @click="pick(family)">
        {{ t('documentTemplates.formImport.originalAdd') }}
      </SecondaryButton>
    </div>
    <input ref="picker" type="file" accept=".ttf,.otf" class="hidden" aria-hidden="true"
           data-testid="form-original-fonts-file" @change="added"/>
  </div>
</template>

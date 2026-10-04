/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FileInput from '@/components/input/FileInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FontStyle} from '@/api/generated/schema'
import type {FontUpload} from '@/api/documentFonts'
import {FONT_STYLES} from './fontOptions'

/**
 * Uploads one style of a font family: the file, the family name templates pick it by, which style it
 * is, and the uploader's word that the owner may use the font. The licence is the owner's to know;
 * a font whose licence bits forbid embedding is refused by the server either way.
 */
defineProps<{
  busy: boolean
}>()

const emit = defineEmits<{
  upload: [upload: FontUpload]
}>()

const {t} = useI18n()

const file = ref<File | null>(null)
const family = ref('')
const style = ref<FontStyle>(FontStyle.REGULAR)
const confirmed = ref(false)

const ready = computed(() => file.value !== null && family.value.trim().length > 0 && confirmed.value)

function submit() {
  if (!file.value || !ready.value) return
  emit('upload', {file: file.value, family: family.value.trim(), style: style.value, confirmed: confirmed.value})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="font-upload">
    <SubHeader>{{ t('documentFonts.uploadTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentFonts.uploadHint') }}</MutedText>
    <FileInput accept=".ttf,.otf,font/ttf,font/otf" :label="t('documentFonts.chooseFile')" @select="chosen => file = chosen"/>
    <div class="grid gap-3 sm:grid-cols-2">
      <LabelledField :label="t('documentFonts.familyLabel')" :help="t('documentFonts.familyHelp')">
        <TextInput v-model="family" data-testid="font-family" :maxlength="60"/>
      </LabelledField>
      <LabelledField :label="t('documentFonts.styleLabel')">
        <SelectInput v-model="style" class="w-full" data-testid="font-style">
          <option v-for="option in FONT_STYLES" :key="option" :value="option">{{ t(`documentFonts.style.${option}`) }}</option>
        </SelectInput>
      </LabelledField>
    </div>
    <ToggleSetting v-model="confirmed" :label="t('documentFonts.licenceLabel')" :hint="t('documentFonts.licenceHint')"/>
    <PrimaryButton :icon="['fas', 'upload']" :disabled="!ready || busy" data-testid="font-upload-submit" @click="submit">
      {{ t('common.upload') }}
    </PrimaryButton>
  </NeutralContainer>
</template>

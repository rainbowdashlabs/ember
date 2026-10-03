/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import {mediaImageUrlAt} from '@/api/media'
import {TextAlign, LetterCellKind, type LetterCell, type Placeholder, type StationFile} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import PlaceholderPicker from './PlaceholderPicker.vue'
import {withPlaceholder} from './placeholderText'

/**
 * One cell of the header or the footer: nothing, a picture from the media library, the station logo,
 * or a few lines of text. Placeholders in a text are written as `{{key}}`; the picker puts one at the
 * end of the text.
 */
const cell = defineModel<LetterCell>({required: true})

defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
const {sessionInfo} = useSession()
const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

const kinds = [LetterCellKind.EMPTY, LetterCellKind.TEXT, LetterCellKind.IMAGE, LetterCellKind.LOGO]
const aligns = [TextAlign.LEFT, TextAlign.CENTER, TextAlign.RIGHT]

const showsPicture = computed(() => cell.value.kind === LetterCellKind.IMAGE || cell.value.kind === LetterCellKind.LOGO)

function setKind(kind: string | number | null | undefined) {
  cell.value = {...cell.value, kind: kind as LetterCellKind}
}

function setAlign(align: string | number | null | undefined) {
  cell.value = {...cell.value, align: align as TextAlign}
}

function pickPicture(payload: {file: StationFile}) {
  cell.value = {...cell.value, mediaHash: payload.file.contentHash}
}

function appendPlaceholder(placeholder: Placeholder) {
  cell.value = {...cell.value, text: withPlaceholder(cell.value.text ?? '', placeholder.key)}
}
</script>

<template>
  <div class="space-y-3 rounded-lg border border-(--border) p-3" data-testid="letter-cell">
    <div class="grid gap-3 sm:grid-cols-2">
      <LabelledField :label="t('documentTemplates.cellKindLabel')">
        <SelectInput :model-value="cell.kind" data-testid="letter-cell-kind" @update:model-value="setKind">
          <option v-for="kind in kinds" :key="kind" :value="kind">{{ t(`documentTemplates.cellKind.${kind}`) }}</option>
        </SelectInput>
      </LabelledField>
      <LabelledField :label="t('documentTemplates.alignLabel')">
        <SelectInput :model-value="cell.align" @update:model-value="setAlign">
          <option v-for="align in aligns" :key="align" :value="align">{{ t(`documentTemplates.align.${align}`) }}</option>
        </SelectInput>
      </LabelledField>
    </div>
    <div v-if="cell.kind === LetterCellKind.TEXT" class="space-y-2">
      <TextAreaInput :model-value="cell.text ?? ''" :rows="3" data-testid="letter-cell-text"
                     @update:model-value="text => cell = {...cell, text: text ?? ''}"/>
      <PlaceholderPicker :placeholders="placeholders" :legal="legal" @pick="appendPlaceholder"/>
    </div>
    <div v-if="cell.kind === LetterCellKind.IMAGE" class="flex flex-wrap items-center gap-3">
      <img v-if="cell.mediaHash" :src="mediaImageUrlAt(stationUid, cell.mediaHash, 200)" alt="" class="h-12 w-auto rounded"/>
      <MediaBrowseButton :station-uid="stationUid" mime-prefix="image/" :label="t('documentTemplates.pickPicture')" @pick="pickPicture"/>
    </div>
    <LabelledField v-if="showsPicture" :label="t('documentTemplates.pictureHeight')">
      <NumberInput :model-value="cell.imageHeightMm" @update:model-value="height => cell = {...cell, imageHeightMm: height ?? 18}"/>
    </LabelledField>
    <div class="flex justify-end">
      <DeleteButton @click="emit('remove')"/>
    </div>
  </div>
</template>

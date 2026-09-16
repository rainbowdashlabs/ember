/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import CoverablePicture from './CoverablePicture.vue'
import {canCoverAutomatically, coversForPersonalData, coversForPasswords} from '@/util/pictureCovers'
import {canCaptureScreen, captureScreen, readPictureFile, scaled, useCovers} from '@/composables/useScreenCapture'

/**
 * The picture a report may carry, from being asked for to being covered.
 *
 * <p>Nothing here happens on its own. No picture is taken when the dialog opens, none when something
 * fails, and none at all until somebody presses for one: a picture of a page of this product is a
 * page of somebody's data, and it is theirs to offer rather than ours to take.
 */
const picture = defineModel<HTMLCanvasElement | null>({required: true})

const {t} = useI18n()
const {covers, add, removeAt, clear} = useCovers()

const working = ref(false)
const refused = ref(false)
const fileInput = ref<HTMLInputElement>()

defineExpose({covers})

async function takePicture() {
  working.value = true
  refused.value = false
  try {
    const taken = await captureScreen()
    if (!taken) {
      refused.value = true
      return
    }
    setPicture(taken)
  } finally {
    working.value = false
  }
}

async function attachFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const read = await readPictureFile(file)
  if (read) setPicture(read)
  if (fileInput.value) fileInput.value.value = ''
}

/**
 * Takes a picture on, scaled to what is worth sending, with the password fields already covered.
 *
 * <p>Those are covered without being asked for, which is the one covering nobody has to choose:
 * there is no report in which the characters of a password belong.
 */
function setPicture(taken: HTMLCanvasElement) {
  const sized = scaled(taken)
  clear()
  picture.value = sized
  coversForPasswords(sized).forEach(add)
}

function discard() {
  picture.value = null
  clear()
}

function coverPersonalData() {
  if (picture.value) coversForPersonalData(picture.value).forEach(add)
}
</script>

<template>
  <div class="space-y-2">
    <p class="text-sm">{{ t('problemReport.pictureTitle') }}</p>
    <MutedText size="sm" tag="p">{{ t('problemReport.pictureHelp') }}</MutedText>

    <Alert v-if="refused" variant="info">
      <p>{{ t('problemReport.pictureRefused') }}</p>
    </Alert>

    <template v-if="picture">
      <CoverablePicture :covers="covers" :picture="picture" @add="add" @remove="removeAt"/>
      <ButtonRow>
        <SecondaryButton
            v-if="canCoverAutomatically(picture)"
            data-testid="picture-cover-personal"
            @click="coverPersonalData"
        >
          {{ t('problemReport.coverPersonalData') }}
        </SecondaryButton>
        <SecondaryButton :disabled="covers.length === 0" @click="clear">
          {{ t('problemReport.coverClear') }}
        </SecondaryButton>
        <SecondaryButton data-testid="picture-discard" @click="discard">
          {{ t('problemReport.pictureDiscard') }}
        </SecondaryButton>
      </ButtonRow>
    </template>

    <ButtonRow v-else>
      <SecondaryButton
          v-if="canCaptureScreen()"
          :disabled="working"
          :icon="['fas', 'camera']"
          data-testid="picture-take"
          @click="takePicture"
      >
        {{ working ? t('common.loading') : t('problemReport.pictureTake') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'upload']" data-testid="picture-attach" @click="fileInput?.click()">
        {{ t('problemReport.pictureAttach') }}
      </SecondaryButton>
    </ButtonRow>

    <input ref="fileInput" accept="image/*" class="hidden" type="file" @change="attachFile"/>
  </div>
</template>

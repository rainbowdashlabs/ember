/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from './Modal.vue'
import Alert from './Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ReportPictureSection from '@/components/problem/ReportPictureSection.vue'
import {submitReport} from '@/api/problemReports'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {flatten} from '@/composables/useScreenCapture'
import {closeProblemReport, problemReportAbout, problemReportOpen} from '@/util/problemReportState'

/**
 * The one form for reporting that Ember itself is behaving wrongly.
 *
 * <p>It is deliberately explicit about where a report goes, because the natural assumption is the
 * opposite one. Everything else in this application is addressed to a station: a ticket, a note, a
 * message to a guardian. This is not: it reaches whoever runs this installation, and nobody at the
 * station ever sees it. Somebody using it to ask their own leadership for a permission would be writing
 * to strangers about something those strangers cannot grant.
 *
 * <p>There is one of these, rendered once by the layout. Anything that wants it calls
 * {@code openProblemReport}, which is what lets a failed request offer a report without every screen
 * carrying a copy of this form.
 */
const {t} = useI18n()
const {sessionInfo} = useSession()

const open = problemReportOpen()
const about = problemReportAbout()

const description = ref('')
const sent = ref(false)
const picture = ref<HTMLCanvasElement | null>(null)
const pictureSection = ref<InstanceType<typeof ReportPictureSection>>()

const enoughSaid = computed(() => description.value.trim().length >= 10)

const {running: sending, error, run: send, clearError} = useAsyncAction(async () => {
  if (!enoughSaid.value) return
  // Flattened here rather than as it was covered: the covers become pixels at the moment of sending,
  // and what leaves has no layer to take off again.
  const covered = picture.value
      ? await flatten(picture.value, pictureSection.value?.covers ?? [])
      : null
  await submitReport(description.value.trim(), sessionInfo.value, about.value ?? undefined, covered)
  sent.value = true
  description.value = ''
  picture.value = null
})

/** A fresh form every time it opens, so yesterday's half-written report is not sent by accident. */
watch(open, (showing) => {
  if (!showing) return
  sent.value = false
  description.value = ''
  picture.value = null
  clearError()
})

function close() {
  closeProblemReport()
}
</script>

<template>
  <Modal :model-value="open" @update:model-value="value => !value && close()">
    <template #title>{{ t('problemReport.title') }}</template>

    <div class="space-y-4">
      <Alert variant="info">
        <p class="font-medium">{{ t('problemReport.notTheStation') }}</p>
        <p class="mt-1">{{ t('problemReport.notTheStationDetail') }}</p>
      </Alert>

      <Alert v-if="error" variant="error">
        <p>{{ error }}</p>
        <p class="mt-1">{{ t('problemReport.couldNotSend') }}</p>
      </Alert>

      <template v-if="sent">
        <Alert variant="success">
          <p>{{ t('problemReport.sent') }}</p>
          <p class="mt-1">{{ t('problemReport.sentDetail') }}</p>
        </Alert>
        <div class="flex justify-end">
          <PrimaryButton @click="close">{{ t('common.close') }}</PrimaryButton>
        </div>
      </template>

      <template v-else>
        <div v-if="about" class="space-y-1">
          <MutedText size="sm" tag="p">{{ t('problemReport.aboutThis') }}</MutedText>
          <p class="rounded-theme bg-(--bg-accent) p-2 text-sm break-words">{{ about.summary }}</p>
        </div>

        <div class="space-y-1">
          <p class="text-sm">{{ t('problemReport.describe') }}</p>
          <MutedText size="sm" tag="p">{{ t('problemReport.describeHelp') }}</MutedText>
          <TextAreaInput
              v-model="description"
              data-testid="problem-report-description"
              :placeholder="t('problemReport.placeholder')"
              :rows="5"
          />
          <MutedText v-if="!enoughSaid && description.trim() !== ''" size="sm" tag="p">
            {{ t('problemReport.tooShort') }}
          </MutedText>
        </div>

        <ReportPictureSection ref="pictureSection" v-model="picture"/>

        <MutedText size="sm" tag="p">{{ t('problemReport.autoCapture') }}</MutedText>

        <ButtonRow pair align="end">
          <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton
              :disabled="sending || !enoughSaid"
              data-testid="problem-report-send"
              @click="send"
          >
            {{ sending ? t('common.loading') : t('problemReport.send') }}
          </PrimaryButton>
        </ButtonRow>
      </template>
    </div>
  </Modal>
</template>

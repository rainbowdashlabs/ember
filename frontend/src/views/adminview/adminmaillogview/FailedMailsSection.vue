/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'
import {
  acknowledgeAllFailedMails,
  acknowledgeFailedMail,
  acknowledgeStationFailedMails,
  getFailedInstanceMails,
  getFailedStationMails,
  requeueFailedMail,
} from '@/api/failedMails'
import type {FailedMail, StationFailedMails} from '@/api/generated/schema'
import FailedInstanceMails from './FailedInstanceMails.vue'
import FailedStationMails from './FailedStationMails.vue'

/**
 * The mails that did not arrive and nobody has taken note of, above the recent mails of the log.
 *
 * <p>The instance's own are listed in full, the stations' counted per station. Taking note ends the
 * warning on the overview and keeps the mail in the log. The section stays out of sight while there is
 * nothing in it; the address `#failed` leads to it.
 */
const props = defineProps<{
  /** Called after anything was taken note of or sent again, so counts elsewhere can follow. */
  onChanged?: () => void
}>()

const PAGE_SIZE = 25

const {t} = useI18n()
const route = useRoute()

const anchor = ref<HTMLElement | null>(null)
const mails = ref<FailedMail[]>([])
const total = ref(0)
const stations = ref<StationFailedMails[]>([])
const busy = ref(false)
const actionFailure = ref<Failure | null>(null)
const scrolled = ref(false)

const {failure: loadFailure, reload} = useAsyncLoader(async () => {
  const [page, groups] = await Promise.all([getFailedInstanceMails(0, PAGE_SIZE), getFailedStationMails()])
  mails.value = page.mails
  total.value = page.total
  stations.value = groups
  await revealWhenAskedFor()
})

/** Scrolls to the section once, when the page was opened at `#failed` and the lists are in. */
async function revealWhenAskedFor() {
  if (scrolled.value || route.hash !== '#failed') return
  scrolled.value = true
  await nextTick()
  anchor.value?.scrollIntoView?.({behavior: 'smooth', block: 'start'})
}

const shown = computed(() => mails.value.length > 0 || stations.value.length > 0 || loadFailure.value !== null)

/** Does one thing to the lists, says how it went, and reads them again. */
async function act(action: () => Promise<unknown>, done: string) {
  busy.value = true
  actionFailure.value = null
  try {
    await action()
    showToast(done, 'success')
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
  await reload()
  props.onChanged?.()
}

async function loadMore() {
  busy.value = true
  actionFailure.value = null
  try {
    const page = await getFailedInstanceMails(Math.ceil(mails.value.length / PAGE_SIZE), PAGE_SIZE)
    const known = new Set(mails.value.map(mail => mail.id))
    mails.value = [...mails.value, ...page.mails.filter(mail => !known.has(mail.id))]
    total.value = page.total
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}

function acknowledge(id: number) {
  return act(() => acknowledgeFailedMail(id), t('failedMails.acknowledged'))
}

/** Takes note of every mail up to the latest failure the list shows, which is its first row. */
function acknowledgeAll() {
  const newest = mails.value[0]
  if (!newest) return
  return act(() => acknowledgeAllFailedMails(newest.failedAt), t('failedMails.acknowledged'))
}

function acknowledgeStation(group: StationFailedMails) {
  return act(() => acknowledgeStationFailedMails(group), t('failedMails.acknowledged'))
}

function requeue(id: number) {
  return act(() => requeueFailedMail(id), t('failedMails.requeued'))
}
</script>

<template>
  <div id="failed" ref="anchor" class="scroll-mt-4">
    <NeutralContainer v-if="shown" class="space-y-4" data-testid="failed-mails-section">
      <SectionHeader>{{ t('failedMails.title') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('failedMails.hint') }}</MutedText>
      <FailureAlert :failure="loadFailure ?? actionFailure"/>
      <FailedInstanceMails
          v-if="mails.length > 0"
          :mails="mails"
          :total="total"
          :busy="busy"
          @acknowledge="acknowledge"
          @acknowledge-all="acknowledgeAll"
          @requeue="requeue"
          @more="loadMore"
      />
      <FailedStationMails v-if="stations.length > 0" :stations="stations" :busy="busy" @acknowledge="acknowledgeStation"/>
    </NeutralContainer>
  </div>
</template>

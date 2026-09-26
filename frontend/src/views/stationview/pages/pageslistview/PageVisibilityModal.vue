/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {Failure} from '@/util/failure'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {PageVisibility, type PageVisibilityName, type StationPage} from '@/api/pageManage'

/**
 * Who reaches a page, as three states rather than a switch.
 *
 * <p>Each says what it means in one line, because "unlisted" on its own tells nobody that the page
 * leaves the station's menu and stops being part of its page tree.
 */
const props = defineProps<{
  page: StationPage | null
  /** Why the chosen visibility was refused, described, or nothing where nothing was. */
  failure: Failure | null
}>()

const emit = defineEmits<{
  (e: 'choose', visibility: PageVisibilityName): void
  (e: 'close'): void
}>()

const {t} = useI18n()

const chosen = ref<PageVisibilityName>(PageVisibility.DRAFT)

/**
 * The dialog is open exactly while a page is being asked about. Closing it says so upwards, since
 * the page it is about is what the caller holds.
 */
const open = computed({
  get: () => props.page !== null,
  set: value => {
    if (!value) emit('close')
  },
})

watch(() => props.page, page => {
  if (page) chosen.value = page.visibility
}, {immediate: true})

const options: {value: PageVisibilityName; labelKey: string; hintKey: string}[] = [
  {value: PageVisibility.DRAFT, labelKey: 'stationPages.visibilityDraft', hintKey: 'stationPages.visibilityDraftHint'},
  {
    value: PageVisibility.UNLISTED,
    labelKey: 'stationPages.visibilityUnlisted',
    hintKey: 'stationPages.visibilityUnlistedHint',
  },
  {value: PageVisibility.PUBLIC, labelKey: 'stationPages.visibilityPublic', hintKey: 'stationPages.visibilityPublicHint'},
]
</script>

<template>
    <Modal v-model="open">
        <div class="space-y-3">
            <SubHeader>{{ t('stationPages.changeVisibility') }}</SubHeader>
            <FailureAlert :failure="failure"/>

            <div
                v-for="option in options"
                :key="option.value"
                class="cursor-pointer rounded-theme border-2 px-4 py-3 transition-colors"
                :class="chosen === option.value
                    ? 'border-primary bg-primary/10'
                    : 'border-(--border) hover:border-primary/50'"
                @click="chosen = option.value">
                <p class="font-medium">{{ t(option.labelKey) }}</p>
                <MutedText tag="p" size="sm">{{ t(option.hintKey) }}</MutedText>
            </div>

            <ButtonRow pair align="end">
                <SecondaryButton @click="emit('close')">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton @click="emit('choose', chosen)">{{ t('common.save') }}</PrimaryButton>
            </ButtonRow>
        </div>
    </Modal>
</template>

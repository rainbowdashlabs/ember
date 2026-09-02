/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LendingShareModal from '@/components/lending/LendingShareModal.vue'
import {useLendingShare} from '@/composables/useLendingShare'
import {useSession} from '@/composables/useSession'
import * as lending from '@/api/lending'
import type {ShareSetting, ShareTarget} from '@/api/lending'

/**
 * What this inventory or this item is offered as, on the screen the gear itself lives on.
 *
 * <p>It renders for nobody but a lending manager, and nowhere the station does not lend: an
 * association issues its gear rather than lending it, and its screens name no lending route. It
 * also says nothing about gear the station does not own, where an offer could never be filled.
 */
const props = withDefaults(defineProps<{
  target: ShareTarget
  targetId: number
  targetName: string
  /** Whether this gear is the station's to lend. Gear of the body above it is not. */
  lendable?: boolean
}>(), {lendable: true})

const {t} = useI18n()
const {loaded} = useSession()
const {visible, stateLabel} = useLendingShare(() => props.lendable)

const setting = ref<ShareSetting | null>(null)
const editorOpen = ref(false)

const state = computed(() => stateLabel(setting.value))

async function load() {
  if (!visible.value) return
  try {
    setting.value = await lending.getShare(props.target, props.targetId)
  } catch {
    setting.value = null
  }
}

onMounted(() => {
  if (loaded.value) load()
})

watch(loaded, (isLoaded) => {
  if (isLoaded) load()
})
</script>

<template>
  <NeutralContainer v-if="visible" class="mt-4" data-testid="lending-share-panel">
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <div class="flex flex-col gap-1">
        <SubHeader>{{ t('lendingShare.panelTitle') }}</SubHeader>
        <MutedText data-testid="lending-share-state">{{ state }}</MutedText>
        <MutedText v-if="setting && !setting.shared">{{ t('lendingShare.stillPromisedHint') }}</MutedText>
      </div>
      <SecondaryButton :icon="['fas', 'share-nodes']" data-testid="lending-share-edit" @click="editorOpen = true">
        {{ t('lendingShare.edit') }}
      </SecondaryButton>
    </div>

    <LendingShareModal
        v-model="editorOpen"
        :target="target"
        :target-id="targetId"
        :target-name="targetName"
        @saved="load"
    />
  </NeutralContainer>
</template>

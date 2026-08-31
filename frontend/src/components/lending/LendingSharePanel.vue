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
 * association issues its gear rather than lending it, and its screens name no lending route.
 */
const props = defineProps<{
  target: ShareTarget
  targetId: number
  targetName: string
}>()

const {t} = useI18n()
const {loaded} = useSession()
const {visible} = useLendingShare()

const setting = ref<ShareSetting | null>(null)
const editorOpen = ref(false)

const stateLabel = computed(() => {
  if (!setting.value?.shared) return t('lendingShare.stateUnshared')
  if (setting.value.grant === 'WITHHOLD') return t('lendingShare.stateWithheld')
  if (setting.value.scope === 'SPECIFIC') return t('lendingShare.stateNamedPartners')
  return t('lendingShare.stateAllPartners')
})

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
        <MutedText data-testid="lending-share-state">{{ stateLabel }}</MutedText>
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

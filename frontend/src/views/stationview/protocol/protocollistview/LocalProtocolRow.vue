/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {TestProtocol} from '@/api/generated/schema'

/** One of the station's own protocols, opening the protocol it names, and whether partners may see it. */
const props = defineProps<{
  protocol: TestProtocol
  canConfigure: boolean
  canShare: boolean
  shared: boolean
  sharing: boolean
}>()

const emit = defineEmits<{
  remove: [protocol: TestProtocol]
  toggleShare: [protocol: TestProtocol]
}>()

const {t} = useI18n()
const router = useRouter()

const protocolPage = computed(() => ({name: 'protocol-detail', params: {id: props.protocol.id}}))
</script>

<template>
  <RowLink :to="protocolPage">
    <NeutralContainer
      class="flex items-center gap-2 cursor-pointer hover:border-[var(--color-primary)] transition-colors group"
    >
      <div class="flex-1 min-w-0">
        <div class="font-medium">{{ protocol.name }}</div>
        <div v-if="protocol.description" class="text-sm text-[var(--text-muted)] truncate">
          {{ protocol.description }}
        </div>
      </div>
      <span v-if="protocol.passThreshold" class="text-xs text-[var(--text-muted)]">
        {{ t('protocol.threshold') }}: {{ protocol.passThreshold }}P
      </span>
      <SecondaryBadge v-if="shared">{{ t('protocol.sharedWithPartners') }}</SecondaryBadge>
      <div v-if="canConfigure || canShare" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <MutedIconButton v-if="canShare" :icon="['fas', 'share-nodes']" :disabled="sharing"
                         :label="shared ? t('protocol.stopSharing') : t('protocol.shareWithPartners')"
                         @click="emit('toggleShare', protocol)"/>
        <EditButton v-if="canConfigure" :label="t('common.edit')" @click="router.push(protocolPage)"/>
        <DeleteButton v-if="canConfigure" :label="t('common.delete')" @click="emit('remove', protocol)"/>
      </div>
    </NeutralContainer>
  </RowLink>
</template>

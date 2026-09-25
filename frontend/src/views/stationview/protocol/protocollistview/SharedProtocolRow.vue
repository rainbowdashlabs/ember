/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import IconButton from '@/components/button/IconButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import type {SharedProtocolEntry} from '@/api/protocol'

/** A partner's protocol, as it is offered in this station's list. */
const props = defineProps<{
  shared: SharedProtocolEntry
}>()

const emit = defineEmits<{
  copy: [protocolId: number]
}>()

const {t} = useI18n()

/**
 * The partner's protocol, where the partner is known. An entry naming no station is one nothing can
 * be opened from, so it stays the plain card it always was.
 */
const protocolPage = computed(() => props.shared.stationUid
    ? {name: 'federated-protocol', params: {stationUid: props.shared.stationUid, protocolId: props.shared.id}}
    : null)
</script>

<template>
  <RowLink :to="protocolPage">
    <NeutralContainer
      class="flex items-center gap-2 hover:border-[var(--primary)] transition-colors group"
      :class="shared.stationUid ? 'cursor-pointer' : ''"
    >
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2">
          <span class="font-medium">{{ shared.name }}</span>
          <StationBadge :station-name="shared.stationName"/>
        </div>
        <div v-if="shared.description" class="text-sm text-[var(--text-muted)] truncate">
          {{ shared.description }}
        </div>
      </div>
      <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <IconButton
          :icon="['fas', 'copy']"
          :label="t('federation.copyToStation')"
          @click="emit('copy', shared.id)"
        />
      </div>
    </NeutralContainer>
  </RowLink>
</template>

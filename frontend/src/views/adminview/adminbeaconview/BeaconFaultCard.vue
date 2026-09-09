/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import {formatDateTime} from '@/util/format'
import type {BeaconFault} from '@/api/beacon'

/**
 * One fault, with the thing a beacon exists to say beside it: how many installations met it.
 */
const props = defineProps<{fault: BeaconFault}>()

const emit = defineEmits<{resolve: [id: number, version: string | null]}>()

const {t} = useI18n()
const expanded = ref(false)

const container = computed(() => props.fault.level === 'ERROR' ? ErrorContainer : InfoContainer)
</script>

<template>
  <component :is="container" class="cursor-pointer" :class="{'opacity-50': fault.acknowledged}" @click="expanded = !expanded">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-semibold break-all">{{ fault.exceptionClass ?? fault.logger }}</span>
          <PrimaryBadge :data-testid="`fault-instances-${fault.id}`">
            {{ t('beacon.instancesAffected', {count: fault.instances}) }}
          </PrimaryBadge>
          <SecondaryBadge v-if="fault.resolvedIn">{{ t('beacon.resolvedIn') }} {{ fault.resolvedIn }}</SecondaryBadge>
        </div>
        <MutedText tag="p" size="sm">
          {{ formatDateTime(fault.firstSeen) }} - {{ formatDateTime(fault.lastSeen) }}
        </MutedText>
        <MutedText v-if="fault.versions.length > 0" tag="p" size="sm">
          {{ t('beacon.versionsSeen') }}: {{ fault.versions.join(', ') }}
        </MutedText>
      </div>
      <IconButton
          v-if="!fault.acknowledged"
          :icon="['fas', 'check']"
          :label="t('beacon.markResolved')"
          @click.stop="emit('resolve', fault.id, fault.versions.at(-1) ?? null)"
      />
    </div>
    <pre v-if="expanded && fault.frames" class="mt-2 max-h-72 overflow-auto text-xs whitespace-pre-wrap break-words">{{ fault.frames }}</pre>
  </component>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import DiscoveryTileHeader from '@/components/discovery/DiscoveryTileHeader.vue'
import DiscoveryOfferChips from '@/components/discovery/DiscoveryOfferChips.vue'
import DiscoveryPageLink from '@/components/discovery/DiscoveryPageLink.vue'
import IconButton from '@/components/button/IconButton.vue'
import {isOnTheMap, isViewersStation, placeLine, showsConnect, showsInvite} from '@/components/discovery/tileRules'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * One station on the discovery page, drawn the same way whether it lives on this instance or on another
 * one: only the host line under the name tells them apart.
 *
 * <p>In the list a station with a place on the map offers to be shown there. Chosen on the map, the tile
 * is drawn `large` below it and offers to be closed instead.
 */
const props = defineProps<{
  station: DiscoveryEntry
  viewer: DiscoveryViewer
  large?: boolean
}>()

const emit = defineEmits<{
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
  locate: [station: DiscoveryEntry]
  close: []
}>()

const {t} = useI18n()

const own = computed(() => isViewersStation(props.station, props.viewer))
const place = computed(() => placeLine(props.station))
const locatable = computed(() => !props.large && isOnTheMap(props.station))
</script>

<template>
  <NeutralContainer class="flex flex-col gap-2" :class="large ? 'sm:p-6' : ''">
    <DiscoveryTileHeader :station="station" :large="large">
      <IconButton v-if="large" :icon="['fas', 'xmark']" :label="t('discovery.closeTile')" @click="emit('close')"/>
    </DiscoveryTileHeader>

    <MutedText v-if="station.description" tag="p" size="sm">{{ station.description }}</MutedText>
    <MutedText v-if="place" tag="p" size="sm" class="flex items-center gap-1.5">
      <AppIcon :icon="['fas', 'location-dot']" aria-hidden="true" class="shrink-0"/>
      {{ place }}
    </MutedText>

    <DiscoveryOfferChips :station="station"/>

    <ButtonRow class="mt-auto pt-2">
      <PrimaryBadge v-if="own">{{ t('discovery.ownStation') }}</PrimaryBadge>
      <SuccessBadge v-else-if="station.alreadyFederated">{{ t('discovery.alreadyConnected') }}</SuccessBadge>
      <PrimaryButton v-if="showsConnect(station, viewer)" compact :icon="['fas', 'handshake']" @click="emit('connect', station)">
        {{ t('discovery.connect') }}
      </PrimaryButton>
      <SecondaryButton v-if="showsInvite(station, viewer)" compact :icon="['fas', 'link']" @click="emit('invite', station)">
        {{ t('discovery.getCode') }}
      </SecondaryButton>
      <DiscoveryPageLink v-if="station.publicPageUrl" :station="station" :href="station.publicPageUrl"/>
      <SecondaryButton v-if="locatable" compact :icon="['fas', 'map-location-dot']" @click="emit('locate', station)">
        {{ t('discovery.showOnMap') }}
      </SecondaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>

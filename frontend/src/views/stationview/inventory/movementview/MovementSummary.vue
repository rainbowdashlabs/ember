/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {MovementState, type Movement} from '@/api/movements'

/**
 * Who and what a movement is about, above the chain it walks.
 *
 * <p>The state is only spelled out once the movement has ended: while it is open, the step it stands
 * on says where it is far better than the word "offen" does.
 */
const {t} = useI18n()

const props = defineProps<{movement: Movement}>()

const open = computed(() => props.movement.state === MovementState.OPEN)
</script>

<template>
  <NeutralContainer class="space-y-1 text-sm">
    <div v-if="props.movement.memberIdentity" class="flex items-center gap-2">
      <span class="text-(--text-muted)">{{ t('movements.member') }}</span>
      <MemberName :identity="props.movement.memberIdentity"/>
    </div>
    <div v-if="props.movement.inventoryName">
      <span class="text-(--text-muted)">{{ t('movements.inventory') }}</span>
      {{ props.movement.inventoryName }}
    </div>
    <div v-if="props.movement.reason">
      <span class="text-(--text-muted)">{{ t('movements.reason') }}</span>
      {{ props.movement.reason }}
    </div>
    <div v-if="props.movement.closeReason">
      <span class="text-(--text-muted)">{{ t('movements.closeReason') }}</span>
      {{ props.movement.closeReason }}
    </div>
    <div v-if="!open">
      <span class="text-(--text-muted)">{{ t('movements.stateLabel') }}</span>
      {{ t(`movements.state.${props.movement.state}`) }}
    </div>
  </NeutralContainer>
</template>

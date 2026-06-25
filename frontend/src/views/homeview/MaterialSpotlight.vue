/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MaterialSpotlightCard from './materialspotlight/MaterialSpotlightCard.vue'
import MaterialSpotlightCopy from './materialspotlight/MaterialSpotlightCopy.vue'
import type {ExchangeRow, ProcurementRow, LossRow} from './materialspotlight/materialBadges'

const {tm, rt} = useI18n()

const exchanges = computed(() =>
    (tm('landing.material.mock.exchanges') as ExchangeRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, owner: rt(r.owner), status: r.status,
    })),
)
const procurements = computed(() =>
    (tm('landing.material.mock.procurements') as ProcurementRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, notes: rt(r.notes), requested: rt(r.requested),
    })),
)
const losses = computed(() =>
    (tm('landing.material.mock.losses') as LossRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, owner: rt(r.owner), lostAt: rt(r.lostAt),
    })),
)
const bullets = computed(() => (tm('landing.material.bullets') as string[]).map(rt))
</script>

<template>
  <section class="landing-section landing-section-bordered">
    <div class="landing-grid-2col landing-grid-2col-wide-left">
      <MaterialSpotlightCard
          :exchanges="exchanges"
          :procurements="procurements"
          :losses="losses"
      />
      <MaterialSpotlightCopy :bullets="bullets"/>
    </div>
  </section>
</template>

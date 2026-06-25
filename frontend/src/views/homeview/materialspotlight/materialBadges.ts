/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'

export type InvType = 'INTERNAL' | 'EXTERNAL' | 'MIXED'
export type ExchangeStatus = 'ANNOUNCED' | 'SHIPPED'

export interface ExchangeRow {item: string; size: string; type: InvType; owner: string; status: ExchangeStatus}
export interface ProcurementRow {item: string; size: string; type: InvType; notes: string; requested: string}
export interface LossRow {item: string; size: string; type: InvType; owner: string; lostAt: string}

/**
 * Resolves the badge component used to render a material type tag in the landing-page spotlight.
 */
export function typeBadge(type: InvType) {
  if (type === 'INTERNAL') return InfoBadge
  if (type === 'EXTERNAL') return SecondaryBadge
  return SuccessBadge
}

/**
 * Resolves the badge component used to render an exchange status tag in the spotlight.
 */
export function exchangeStatusBadge(status: ExchangeStatus) {
  return status === 'SHIPPED' ? InfoBadge : SecondaryBadge
}

/**
 * Composable returning translation helpers for the spotlight badges.
 */
export function useMaterialLabels() {
  const {t} = useI18n()
  return {
    typeLabel: (type: InvType) => t(`landing.material.type.${type}`),
    exchangeStatusLabel: (status: ExchangeStatus) => t(`landing.material.exchangeStatus.${status}`),
  }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EndpointStats} from '@/api/apiStatus'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import {formatMs, formatPercent} from './apiStatusFormat'

/** The HTTP methods an endpoint answers to, in the order the method column sorts them. */
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const

function durationColumn(key: string, label: string, value: (endpoint: EndpointStats) => number): TableColumn<EndpointStats> {
    return {key, label, type: ColumnTypes.NUMBER, value, display: endpoint => formatMs(value(endpoint)), align: 'right'}
}

/** The columns of the endpoint statistics: which endpoint, how often it was asked, how fast and how reliably it answered. */
export function endpointColumns(t: (key: string) => string): TableColumn<EndpointStats>[] {
    return [
        {
            key: 'method', label: t('apiStatus.method'), type: ColumnTypes.ENUM,
            value: endpoint => endpoint.method, options: enumOptions(METHODS, method => method),
        },
        {key: 'path', label: t('apiStatus.endpoint'), type: ColumnTypes.TEXT, value: endpoint => endpoint.path, pinned: true},
        {key: 'count', label: t('apiStatus.count'), type: ColumnTypes.NUMBER, value: endpoint => endpoint.requestCount, align: 'right'},
        durationColumn('avg', t('apiStatus.avg'), endpoint => endpoint.avgDurationMs),
        durationColumn('min', t('apiStatus.min'), endpoint => endpoint.minDurationMs),
        durationColumn('max', t('apiStatus.max'), endpoint => endpoint.maxDurationMs),
        {
            key: 'errorRate', label: t('apiStatus.errorRate'), type: ColumnTypes.NUMBER,
            value: endpoint => endpoint.errorRate * 100, display: endpoint => formatPercent(endpoint.errorRate), align: 'right',
        },
    ]
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {browserRef} from '@/util/browserState'
import {renderRequest} from './ssr'

const moduleLevel = ref('')
const browserLevel = browserRef('')

/** Writes its name into the given state while it sets up, and draws what the state holds. */
function writer(write: (name: string) => void, read: () => string) {
    return defineComponent({
        props: {name: {type: String, default: ''}},
        setup(props) {
            if (props.name) write(props.name)
            return () => h('p', read())
        },
    })
}

describe('rendering the way the server renders a request', () => {
    it('gives every render its own request state', async () => {
        const Header = writer(
            name => useState('ssr.test', () => '').value = name,
            () => useState('ssr.test', () => '').value,
        )

        await renderRequest(() => h(Header, {name: 'Wache Nord'}))
        const next = await renderRequest(() => h(Header))

        expect(next).toBe('<p></p>')
    })

    it('shares module state between renders, which is what a leak looks like', async () => {
        const Header = writer(name => moduleLevel.value = name, () => moduleLevel.value)

        await renderRequest(() => h(Header, {name: 'Wache Nord'}))
        const next = await renderRequest(() => h(Header))

        expect(next).toBe('<p>Wache Nord</p>')
    })

    it('fails a render that writes browser state', async () => {
        const Header = writer(name => browserLevel.value = name, () => browserLevel.value)

        await expect(renderRequest(() => h(Header, {name: 'Wache Nord'}))).rejects.toThrow('readonly')
    })
})

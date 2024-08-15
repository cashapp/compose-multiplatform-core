/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test implementation details of the true implementation of ScatterMap; these behaviors do not
 * hold on the Kotlin/JS implementation that just delegates to [Map].
 */
class ScatterMapNonJsTest {
    @Test
    fun asMutableMapValuesIteratorRemoveWithoutNext() {
        val map = mutableScatterMapOf("Hello" to "World")
        val mutableMap = map.asMutableMap()
        val values = mutableMap.values

        // No-op before a call to next()
        val iterator = values.iterator()
        iterator.remove()
        assertEquals(1, map.size)
    }

    @Test
    fun asMutableMapValuesIterationOrder() {
        val map = MutableScatterMap<String, String>()
        map["Hello"] = "World"
        map["Bonjour"] = "Monde"
        map["Hallo"] = "Welt"

        val iterator = map.asMutableMap().values.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("Monde", iterator.next())
    }

    @Test
    fun asMutableMapKeysIteratorRemoveWithoutNext() {
        val map = mutableScatterMapOf("Hello" to "World")
        val mutableMap = map.asMutableMap()
        val keys = mutableMap.keys

        // No-op before a call to next()
        val iterator = keys.iterator()
        iterator.remove()
        assertEquals(1, map.size)
    }

    @Test
    fun asMutableMapKeysIterationOrder() {
        val map = MutableScatterMap<String, String>()
        map["Hello"] = "World"
        map["Bonjour"] = "Monde"
        map["Hallo"] = "Welt"

        val iterator = map.asMutableMap().keys.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("Bonjour", iterator.next())
    }

    @Test
    fun asMutableMapEntriesIteratorRemoveWithoutNext() {
        val map = mutableScatterMapOf("Hello" to "World")
        val mutableMap = map.asMutableMap()
        val entries = mutableMap.entries

        // No-op before a call to next()
        val iterator = entries.iterator()
        iterator.remove()
        assertEquals(1, map.size)
    }

    @Test
    fun asMutableMapEntriesIterationOrder() {
        val map = MutableScatterMap<String, String>()
        map["Hello"] = "World"
        map["Bonjour"] = "Monde"
        map["Hallo"] = "Welt"

        val iterator = map.asMutableMap().entries.iterator()
        assertTrue(iterator.hasNext())
        val next = iterator.next()
        assertEquals("Bonjour", next.key)
        assertEquals("Monde", next.value)
    }
}

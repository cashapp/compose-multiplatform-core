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

@file:Suppress(
    "RedundantVisibilityModifier",
    "KotlinRedundantDiagnosticSuppress",
    "KotlinConstantConditions",
    "PropertyName",
    "ConstPropertyName",
    "PrivatePropertyName",
    "NOTHING_TO_INLINE"
)

package androidx.collection

public actual sealed class ScatterMap<K, V> protected constructor(
    private val delegate: Map<K, V>,
) {
    public actual val capacity: Int
        get() = 0

    public actual val size: Int
        get() = delegate.size

    public actual fun any(): Boolean = size != 0

    public actual fun none(): Boolean = size == 0

    public actual fun isEmpty(): Boolean = size == 0

    public actual fun isNotEmpty(): Boolean = size != 0

    public actual operator fun get(key: K): V? = delegate.get(key)

    public actual fun getOrDefault(key: K, defaultValue: V): V {
        val result = delegate[key]
        return when {
            result != null -> result
            delegate.containsKey(key) -> null as V
            else -> defaultValue
        }
    }

    public actual inline fun getOrElse(key: K, defaultValue: () -> V): V {
        return get(key) ?: defaultValue()
    }

    public actual inline fun forEach(block: (key: K, value: V) -> Unit) {
        asMap().forEach { (k, v) ->
            block(k, v)
        }
    }

    public actual inline fun forEachKey(block: (key: K) -> Unit) {
        asMap().keys.forEach(block)
    }

    public actual inline fun forEachValue(block: (value: V) -> Unit) {
        asMap().values.forEach(block)
    }

    public actual inline fun all(predicate: (K, V) -> Boolean): Boolean {
        forEach { key, value ->
            if (!predicate(key, value)) return false
        }
        return true
    }

    public actual inline fun any(predicate: (K, V) -> Boolean): Boolean {
        forEach { key, value ->
            if (predicate(key, value)) return true
        }
        return false
    }

    public actual fun count(): Int = size

    public actual inline fun count(predicate: (K, V) -> Boolean): Int {
        var count = 0
        forEach { key, value ->
            if (predicate(key, value)) count++
        }
        return count
    }

    public actual operator fun contains(key: K): Boolean = delegate.contains(key)

    public actual fun containsKey(key: K): Boolean = delegate.contains(key)

    public actual fun containsValue(value: V): Boolean {
        forEachValue { v ->
            if (value == v) return true
        }
        return false
    }

    public actual fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((key: K, value: V) -> CharSequence)?,
    ): String = buildString {
        append(prefix)
        var index = 0
        this@ScatterMap.forEach { key, value ->
            if (index == limit) {
                append(truncated)
                return@buildString
            }
            if (index != 0) {
                append(separator)
            }
            if (transform == null) {
                append(key)
                append('=')
                append(value)
            } else {
                append(transform(key, value))
            }
            index++
        }
        append(postfix)
    }

    public actual override fun hashCode(): Int = delegate.hashCode()

    public actual override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        return other is ScatterMap<*, *> && delegate == other.delegate
    }

    public actual override fun toString(): String {
        if (isEmpty()) {
            return "{}"
        }

        val s = StringBuilder().append('{')
        var i = 0
        forEach { key, value ->
            s.append(if (key === this) "(this)" else key)
            s.append("=")
            s.append(if (value === this) "(this)" else value)
            i++
            if (i < size) {
                s.append(',').append(' ')
            }
        }

        return s.append('}').toString()
    }

    public actual fun asMap(): Map<K, V> = delegate
}

public actual class MutableScatterMap<K, V> internal constructor(
    private val mutableDelegate: MutableMap<K, V>,
) : ScatterMap<K, V>(mutableDelegate) {

    public actual constructor(
        initialCapacity: Int
    ) : this(mutableMapOf())

    public actual inline fun getOrPut(key: K, defaultValue: () -> V): V {
        return get(key) ?: defaultValue().also { set(key, it) }
    }

    public actual inline fun compute(key: K, computeBlock: (key: K, value: V?) -> V): V {
        val value = this[key]
        val computedValue = computeBlock(key, value)
        put(key, computedValue)
        return computedValue
    }

    public actual operator fun set(key: K, value: V) {
        mutableDelegate[key] = value
    }

    public actual fun put(key: K, value: V): V? {
        return mutableDelegate.put(key, value)
    }

    public actual fun putAll(@Suppress("ArrayReturn") pairs: Array<out Pair<K, V>>) {
        mutableDelegate.putAll(pairs)
    }

    public actual fun putAll(pairs: Iterable<Pair<K, V>>) {
        mutableDelegate.putAll(pairs)
    }

    public actual fun putAll(pairs: Sequence<Pair<K, V>>) {
        mutableDelegate.putAll(pairs)
    }

    public actual fun putAll(from: Map<K, V>) {
        mutableDelegate.putAll(from)
    }

    public actual fun putAll(from: ScatterMap<K, V>) {
        mutableDelegate.putAll(from.asMap())
    }

    public actual inline operator fun plusAssign(pair: Pair<K, V>) {
        asMutableMap().plusAssign(pair)
    }

    public actual inline operator fun plusAssign(
        @Suppress("ArrayReturn") pairs: Array<out Pair<K, V>>
    ): Unit = asMutableMap().plusAssign(pairs)

    public actual inline operator fun plusAssign(pairs: Iterable<Pair<K, V>>): Unit = asMutableMap().plusAssign(pairs)

    public actual inline operator fun plusAssign(pairs: Sequence<Pair<K, V>>): Unit = asMutableMap().plusAssign(pairs)

    public actual inline operator fun plusAssign(from: Map<K, V>): Unit = asMutableMap().plusAssign(from)

    public actual inline operator fun plusAssign(from: ScatterMap<K, V>): Unit = asMutableMap().plusAssign(from.asMap())

    public actual fun remove(key: K): V? {
        return mutableDelegate.remove(key)
    }

    public actual fun remove(key: K, value: V): Boolean {
        if (mutableDelegate[key] == value) {
            mutableDelegate.remove(key)
            return true
        }
        return false
    }

    public actual inline fun removeIf(predicate: (K, V) -> Boolean) {
        val i = asMutableMap().iterator()
        while (i.hasNext()) {
            val (key, value) = i.next()
            if (predicate(key, value)) {
                i.remove()
            }
        }
    }

    public actual inline operator fun minusAssign(key: K) {
        asMutableMap().minusAssign(key)
    }

    public actual inline operator fun minusAssign(@Suppress("ArrayReturn") keys: Array<out K>) {
        asMutableMap().minusAssign(keys)
    }

    public actual inline operator fun minusAssign(keys: Iterable<K>) {
        asMutableMap().minusAssign(keys)
    }

    public actual inline operator fun minusAssign(keys: Sequence<K>) {
        asMutableMap().minusAssign(keys)
    }

    public actual inline operator fun minusAssign(keys: ScatterSet<K>) {
        asMutableMap().minusAssign(keys.asSet())
    }

    public actual inline operator fun minusAssign(keys: ObjectList<K>) {
        keys.forEach { key ->
            remove(key)
        }
    }

    public actual fun clear() {
        mutableDelegate.clear()
    }

    public actual fun trim(): Int {
        return 0
    }

    public actual fun asMutableMap(): MutableMap<K, V> = mutableDelegate
}
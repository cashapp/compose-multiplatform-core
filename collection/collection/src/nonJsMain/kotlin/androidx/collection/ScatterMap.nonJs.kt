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

import androidx.collection.internal.EMPTY_OBJECTS
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.math.max


public actual sealed class ScatterMap<K, V> {
    // NOTE: Our arrays are marked internal to implement inlined forEach{}
    // The backing array for the metadata bytes contains
    // `capacity + 1 + ClonedMetadataCount` entries, including when
    // the table is empty (see [EmptyGroup]).
    @PublishedApi
    @JvmField
    internal var metadata: LongArray = EmptyGroup

    @PublishedApi
    @JvmField
    internal var keys: Array<Any?> = EMPTY_OBJECTS

    @PublishedApi
    @JvmField
    internal var values: Array<Any?> = EMPTY_OBJECTS

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the capacity
    @JvmField
    internal var _capacity: Int = 0

    public actual val capacity: Int
        get() = _capacity

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the size
    @JvmField
    internal var _size: Int = 0

    public actual val size: Int
        get() = _size

    public actual fun any(): Boolean = _size != 0

    public actual fun none(): Boolean = _size == 0

    public actual fun isEmpty(): Boolean = _size == 0

    public actual fun isNotEmpty(): Boolean = _size != 0

    public actual operator fun get(key: K): V? {
        val index = findKeyIndex(key)
        @Suppress("UNCHECKED_CAST")
        return if (index >= 0) values[index] as V? else null
    }

    public actual fun getOrDefault(key: K, defaultValue: V): V {
        val index = findKeyIndex(key)
        if (index >= 0) {
            @Suppress("UNCHECKED_CAST")
            return values[index] as V
        }
        return defaultValue
    }

    public actual inline fun getOrElse(key: K, defaultValue: () -> V): V {
        return get(key) ?: defaultValue()
    }

    /**
     * Iterates over every key/value pair stored in this map by invoking
     * the specified [block] lambda.
     */
    @PublishedApi
    internal inline fun forEachIndexed(block: (index: Int) -> Unit) {
        val m = metadata
        val lastIndex = m.size - 2 // We always have 0 or at least 2 entries

        for (i in 0..lastIndex) {
            var slot = m[i]
            if (slot.maskEmptyOrDeleted() != BitmaskMsb) {
                // Branch-less if (i == lastIndex) 7 else 8
                // i - lastIndex returns a negative value when i < lastIndex,
                // so 1 is set as the MSB. By inverting and shifting we get
                // 0 when i < lastIndex, 1 otherwise.
                val bitCount = 8 - ((i - lastIndex).inv() ushr 31)
                for (j in 0 until bitCount) {
                    if (isFull(slot and 0xFFL)) {
                        val index = (i shl 3) + j
                        block(index)
                    }
                    slot = slot shr 8
                }
                if (bitCount != 8) return
            }
        }
    }

    public actual inline fun forEach(block: (key: K, value: V) -> Unit) {
        val k = keys
        val v = values

        forEachIndexed { index ->
            @Suppress("UNCHECKED_CAST")
            block(k[index] as K, v[index] as V)
        }
    }

    public actual inline fun forEachKey(block: (key: K) -> Unit) {
        val k = keys

        forEachIndexed { index ->
            @Suppress("UNCHECKED_CAST")
            block(k[index] as K)
        }
    }

    public actual inline fun forEachValue(block: (value: V) -> Unit) {
        val v = values

        forEachIndexed { index ->
            @Suppress("UNCHECKED_CAST")
            block(v[index] as V)
        }
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

    /**
     * Returns the number of entries matching the given [predicate].
     */
    public actual inline fun count(predicate: (K, V) -> Boolean): Int {
        var count = 0
        forEach { key, value ->
            if (predicate(key, value)) count++
        }
        return count
    }

    public actual operator fun contains(key: K): Boolean = findKeyIndex(key) >= 0

    public actual fun containsKey(key: K): Boolean = findKeyIndex(key) >= 0

    public actual fun containsValue(value: V): Boolean {
        forEachValue { v ->
            if (value == v) return true
        }
        return false
    }

    @JvmOverloads
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

    /**
     * Returns the hash code value for this map. The hash code the sum of the hash
     * codes of each key/value pair.
     */
    public override fun hashCode(): Int {
        var hash = 0

        forEach { key, value ->
            hash += key.hashCode() xor value.hashCode()
        }

        return hash
    }

    /**
     * Compares the specified object [other] with this hash map for equality.
     * The two objects are considered equal if [other]:
     * - Is a [ScatterMap]
     * - Has the same [size] as this map
     * - Contains key/value pairs equal to this map's pair
     */
    public override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is ScatterMap<*, *>) {
            return false
        }
        if (other.size != size) {
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val o = other as ScatterMap<Any?, Any?>

        forEach { key, value ->
            if (value == null) {
                if (o[key] != null || !o.containsKey(key)) {
                    return false
                }
            } else if (value != o[key]) {
                return false
            }
        }

        return true
    }

    /**
     * Returns a string representation of this map. The map is denoted in the
     * string by the `{}`. Each key/value pair present in the map is represented
     * inside '{}` by a substring of the form `key=value`, and pairs are
     * separated by `, `.
     */
    public override fun toString(): String {
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
            if (i < _size) {
                s.append(',').append(' ')
            }
        }

        return s.append('}').toString()
    }

    internal fun asDebugString(): String = buildString {
        append('{')
        append("metadata=[")
        for (i in 0 until capacity) {
            when (val metadata = readRawMetadata(metadata, i)) {
                Empty -> append("Empty")
                Deleted -> append("Deleted")
                else -> append(metadata)
            }
            append(", ")
        }
        append("], ")
        append("keys=[")
        for (i in keys.indices) {
            append(keys[i])
            append(", ")
        }
        append("], ")
        append("values=[")
        for (i in values.indices) {
            append(values[i])
            append(", ")
        }
        append("]")
        append('}')
    }

    /**
     * Scans the hash table to find the index in the backing arrays of the
     * specified [key]. Returns -1 if the key is not present.
     */
    internal inline fun findKeyIndex(key: K): Int {
        val hash = hash(key)
        val hash2 = h2(hash)

        val probeMask = _capacity
        var probeOffset = h1(hash) and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            var m = g.match(hash2)
            while (m.hasNext()) {
                val index = (probeOffset + m.get()) and probeMask
                if (keys[index] == key) {
                    return index
                }
                m = m.next()
            }

            if (g.maskEmpty() != 0L) {
                break
            }

            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }

        return -1
    }

    public actual fun asMap(): Map<K, V> = MapWrapper()

    // TODO: While not mandatory, it would be pertinent to throw a
    //       ConcurrentModificationException when the underlying ScatterMap
    //       is modified while iterating over keys/values/entries. To do
    //       this we should probably have some kind of generation ID in
    //       ScatterMap that would be incremented on any add/remove/clear
    //       or rehash.
    //
    // TODO: the proliferation of inner classes causes unnecessary code to be
    //       created. For instance, `entries.size` below requires a total of
    //       3 `getfield` to resolve the chain of `this` before getting the
    //       `_size` field. This is likely bad in the various loops like
    //       `containsAll()` etc. We should probably instead create named
    //       classes that take a `ScatterMap` as a parameter to refer to it
    //       directly.
    internal open inner class MapWrapper : Map<K, V> {
        override val entries: Set<Map.Entry<K, V>>
            get() = object : Set<Map.Entry<K, V>> {
                override val size: Int get() = this@ScatterMap._size

                override fun isEmpty(): Boolean = this@ScatterMap.isEmpty()

                override fun iterator(): Iterator<Map.Entry<K, V>> {
                    return iterator {
                        this@ScatterMap.forEachIndexed { index ->
                            @Suppress("UNCHECKED_CAST")
                            yield(
                                MapEntry(
                                    this@ScatterMap.keys[index] as K,
                                    this@ScatterMap.values[index] as V
                                )
                            )
                        }
                    }
                }

                override fun containsAll(elements: Collection<Map.Entry<K, V>>): Boolean =
                    elements.all { this@ScatterMap[it.key] == it.value }

                override fun contains(element: Map.Entry<K, V>): Boolean =
                    this@ScatterMap[element.key] == element.value
            }

        override val keys: Set<K>
            get() = object : Set<K> {
                override val size: Int get() = this@ScatterMap._size

                override fun isEmpty(): Boolean = this@ScatterMap.isEmpty()

                override fun iterator(): Iterator<K> = iterator {
                    this@ScatterMap.forEachKey { key ->
                        yield(key)
                    }
                }

                override fun containsAll(elements: Collection<K>): Boolean =
                    elements.all { this@ScatterMap.containsKey(it) }

                override fun contains(element: K): Boolean = this@ScatterMap.containsKey(element)
            }

        override val values: Collection<V>
            get() = object : Collection<V> {
                override val size: Int get() = this@ScatterMap._size

                override fun isEmpty(): Boolean = this@ScatterMap.isEmpty()

                override fun iterator(): Iterator<V> = iterator {
                    this@ScatterMap.forEachValue { value ->
                        yield(value)
                    }
                }

                override fun containsAll(elements: Collection<V>): Boolean =
                    elements.all { this@ScatterMap.containsValue(it) }

                override fun contains(element: V): Boolean = this@ScatterMap.containsValue(element)
            }

        override val size: Int get() = this@ScatterMap._size

        override fun isEmpty(): Boolean = this@ScatterMap.isEmpty()

        // TODO: @Suppress required because of a lint check issue (b/294130025)
        override fun get(@Suppress("MissingNullability") key: K): V? = this@ScatterMap[key]

        override fun containsValue(value: V): Boolean = this@ScatterMap.containsValue(value)

        override fun containsKey(key: K): Boolean = this@ScatterMap.containsKey(key)
    }
}

public actual class MutableScatterMap<K, V> actual constructor(
    initialCapacity: Int,
) : ScatterMap<K, V>() {
    // Number of entries we can add before we need to grow
    private var growthLimit = 0

    init {
        require(initialCapacity >= 0) { "Capacity must be a positive value." }
        initializeStorage(unloadedCapacity(initialCapacity))
    }

    private fun initializeStorage(initialCapacity: Int) {
        val newCapacity = if (initialCapacity > 0) {
            // Since we use longs for storage, our capacity is never < 7, enforce
            // it here. We do have a special case for 0 to create small empty maps
            max(7, normalizeCapacity(initialCapacity))
        } else {
            0
        }
        _capacity = newCapacity
        initializeMetadata(newCapacity)
        keys = arrayOfNulls(newCapacity)
        values = arrayOfNulls(newCapacity)
    }

    private fun initializeMetadata(capacity: Int) {
        metadata = if (capacity == 0) {
            EmptyGroup
        } else {
            // Round up to the next multiple of 8 and find how many longs we need
            val size = (((capacity + 1 + ClonedMetadataCount) + 7) and 0x7.inv()) shr 3
            LongArray(size).apply {
                fill(AllEmpty)
            }
        }
        writeRawMetadata(metadata, capacity, Sentinel)
        initializeGrowth()
    }

    private fun initializeGrowth() {
        growthLimit = loadedCapacity(capacity) - _size
    }

    public actual inline fun getOrPut(key: K, defaultValue: () -> V): V {
        return get(key) ?: defaultValue().also { set(key, it) }
    }

    public actual inline fun compute(key: K, computeBlock: (key: K, value: V?) -> V): V {
        val index = findInsertIndex(key)
        val inserting = index < 0

        @Suppress("UNCHECKED_CAST")
        val computedValue = computeBlock(
            key,
            if (inserting) null else values[index] as V
        )

        // Skip Array.set() if key is already there
        if (inserting) {
            val insertionIndex = index.inv()
            keys[insertionIndex] = key
            values[insertionIndex] = computedValue
        } else {
            values[index] = computedValue
        }
        return computedValue
    }

    public actual operator fun set(key: K, value: V) {
        val index = findInsertIndex(key).let { index ->
            if (index < 0) index.inv() else index
        }
        keys[index] = key
        values[index] = value
    }

    public actual fun put(key: K, value: V): V? {
        val index = findInsertIndex(key).let { index ->
            if (index < 0) index.inv() else index
        }
        val oldValue = values[index]
        keys[index] = key
        values[index] = value

        @Suppress("UNCHECKED_CAST")
        return oldValue as V?
    }

    public actual fun putAll(@Suppress("ArrayReturn") pairs: Array<out Pair<K, V>>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }

    public actual fun putAll(pairs: Iterable<Pair<K, V>>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }

    public actual fun putAll(pairs: Sequence<Pair<K, V>>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }

    public actual fun putAll(from: Map<K, V>) {
        from.forEach { (key, value) ->
            this[key] = value
        }
    }

    public actual fun putAll(from: ScatterMap<K, V>) {
        from.forEach { key, value ->
            this[key] = value
        }
    }

    public actual inline operator fun plusAssign(pair: Pair<K, V>) {
        this[pair.first] = pair.second
    }

    public actual inline operator fun plusAssign(
        @Suppress("ArrayReturn") pairs: Array<out Pair<K, V>>
    ): Unit = putAll(pairs)

    public actual inline operator fun plusAssign(pairs: Iterable<Pair<K, V>>): Unit = putAll(pairs)

    public actual inline operator fun plusAssign(pairs: Sequence<Pair<K, V>>): Unit = putAll(pairs)

    public actual inline operator fun plusAssign(from: Map<K, V>): Unit = putAll(from)

    public actual inline operator fun plusAssign(from: ScatterMap<K, V>): Unit = putAll(from)

    public actual fun remove(key: K): V? {
        val index = findKeyIndex(key)
        if (index >= 0) {
            return removeValueAt(index)
        }
        return null
    }

    public actual fun remove(key: K, value: V): Boolean {
        val index = findKeyIndex(key)
        if (index >= 0) {
            if (values[index] == value) {
                removeValueAt(index)
                return true
            }
        }
        return false
    }

    public actual inline fun removeIf(predicate: (K, V) -> Boolean) {
        forEachIndexed { index ->
            @Suppress("UNCHECKED_CAST")
            if (predicate(keys[index] as K, values[index] as V)) {
                removeValueAt(index)
            }
        }
    }

    public actual inline operator fun minusAssign(key: K) {
        remove(key)
    }

    public actual inline operator fun minusAssign(@Suppress("ArrayReturn") keys: Array<out K>) {
        for (key in keys) {
            remove(key)
        }
    }

    public actual inline operator fun minusAssign(keys: Iterable<K>) {
        for (key in keys) {
            remove(key)
        }
    }

    public actual inline operator fun minusAssign(keys: Sequence<K>) {
        for (key in keys) {
            remove(key)
        }
    }

    public actual inline operator fun minusAssign(keys: ScatterSet<K>) {
        keys.forEach { key ->
            remove(key)
        }
    }

    public actual inline operator fun minusAssign(keys: ObjectList<K>) {
        keys.forEach { key ->
            remove(key)
        }
    }

    @PublishedApi
    internal fun removeValueAt(index: Int): V? {
        _size -= 1

        // TODO: We could just mark the entry as empty if there's a group
        //       window around this entry that was already empty
        writeMetadata(index, Deleted)
        keys[index] = null
        val oldValue = values[index]
        values[index] = null

        @Suppress("UNCHECKED_CAST")
        return oldValue as V?
    }

    public actual fun clear() {
        _size = 0
        if (metadata !== EmptyGroup) {
            metadata.fill(AllEmpty)
            writeRawMetadata(metadata, _capacity, Sentinel)
        }
        values.fill(null, 0, _capacity)
        keys.fill(null, 0, _capacity)
        initializeGrowth()
    }

    /**
     * Scans the hash table to find the index at which we can store a value
     * for the give [key]. If the key already exists in the table, its index
     * will be returned, otherwise the `index.inv()` of an empty slot will be returned.
     * Calling this function may cause the internal storage to be reallocated
     * if the table is full.
     */
    @PublishedApi
    internal fun findInsertIndex(key: K): Int {
        val hash = hash(key)
        val hash1 = h1(hash)
        val hash2 = h2(hash)

        val probeMask = _capacity
        var probeOffset = hash1 and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            var m = g.match(hash2)
            while (m.hasNext()) {
                val index = (probeOffset + m.get()) and probeMask
                if (keys[index] == key) {
                    return index
                }
                m = m.next()
            }

            if (g.maskEmpty() != 0L) {
                break
            }

            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }

        var index = findFirstAvailableSlot(hash1)
        if (growthLimit == 0 && !isDeleted(metadata, index)) {
            adjustStorage()
            index = findFirstAvailableSlot(hash1)
        }

        _size += 1
        growthLimit -= if (isEmpty(metadata, index)) 1 else 0
        writeMetadata(index, hash2.toLong())

        return index.inv()
    }

    /**
     * Finds the first empty or deleted slot in the table in which we can
     * store a value without resizing the internal storage.
     */
    private fun findFirstAvailableSlot(hash1: Int): Int {
        val probeMask = _capacity
        var probeOffset = hash1 and probeMask
        var probeIndex = 0

        while (true) {
            val g = group(metadata, probeOffset)
            val m = g.maskEmptyOrDeleted()
            if (m != 0L) {
                return (probeOffset + m.lowestBitSet()) and probeMask
            }
            probeIndex += GroupWidth
            probeOffset = (probeOffset + probeIndex) and probeMask
        }
    }

    public actual fun trim(): Int {
        val previousCapacity = _capacity
        val newCapacity = normalizeCapacity(unloadedCapacity(_size))
        if (newCapacity < previousCapacity) {
            resizeStorage(newCapacity)
            return previousCapacity - _capacity
        }
        return 0
    }

    /**
     * Grow internal storage if necessary. This function can instead opt to
     * remove deleted entries from the table to avoid an expensive reallocation
     * of the underlying storage. This "rehash in place" occurs when the
     * current size is <= 25/32 of the table capacity. The choice of 25/32 is
     * detailed in the implementation of abseil's `raw_hash_set`.
     */
    private fun adjustStorage() {
        if (_capacity > GroupWidth && _size.toULong() * 32UL <= _capacity.toULong() * 25UL) {
            // TODO: Avoid resize and drop deletes instead
            resizeStorage(nextCapacity(_capacity))
        } else {
            resizeStorage(nextCapacity(_capacity))
        }
    }

    private fun resizeStorage(newCapacity: Int) {
        val previousMetadata = metadata
        val previousKeys = keys
        val previousValues = values
        val previousCapacity = _capacity

        initializeStorage(newCapacity)

        val newKeys = keys
        val newValues = values

        for (i in 0 until previousCapacity) {
            if (isFull(previousMetadata, i)) {
                val previousKey = previousKeys[i]
                val hash = hash(previousKey)
                val index = findFirstAvailableSlot(h1(hash))

                writeMetadata(index, h2(hash).toLong())
                newKeys[index] = previousKey
                newValues[index] = previousValues[i]
            }
        }
    }

    /**
     * Writes the "H2" part of an entry into the metadata array at the specified
     * [index]. The index must be a valid index. This function ensures the
     * metadata is also written in the clone area at the end.
     */
    private inline fun writeMetadata(index: Int, value: Long) {
        val m = metadata
        writeRawMetadata(m, index, value)

        // Mirroring
        val c = _capacity
        val cloneIndex = ((index - ClonedMetadataCount) and c) +
            (ClonedMetadataCount and c)
        writeRawMetadata(m, cloneIndex, value)
    }

    public actual fun asMutableMap(): MutableMap<K, V> = MutableMapWrapper()

    // TODO: See TODO on `MapWrapper`
    private inner class MutableMapWrapper : MapWrapper(), MutableMap<K, V> {
        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = object : MutableSet<MutableMap.MutableEntry<K, V>> {
                override val size: Int get() = this@MutableScatterMap._size

                override fun isEmpty(): Boolean = this@MutableScatterMap.isEmpty()

                override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                    object : MutableIterator<MutableMap.MutableEntry<K, V>> {

                        var iterator: Iterator<MutableMap.MutableEntry<K, V>>
                        var current = -1

                        init {
                            iterator = iterator {
                                this@MutableScatterMap.forEachIndexed { index ->
                                    current = index
                                    yield(
                                        MutableMapEntry(
                                            this@MutableScatterMap.keys,
                                            this@MutableScatterMap.values,
                                            current
                                        )
                                    )
                                }
                            }
                        }

                        override fun hasNext(): Boolean = iterator.hasNext()

                        override fun next(): MutableMap.MutableEntry<K, V> = iterator.next()

                        override fun remove() {
                            if (current != -1) {
                                this@MutableScatterMap.removeValueAt(current)
                                current = -1
                            }
                        }
                    }

                override fun clear() {
                    this@MutableScatterMap.clear()
                }

                override fun containsAll(
                    elements: Collection<MutableMap.MutableEntry<K, V>>
                ): Boolean {
                    return elements.all { this@MutableScatterMap[it.key] == it.value }
                }

                override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
                    this@MutableScatterMap[element.key] == element.value

                override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun retainAll(
                    elements: Collection<MutableMap.MutableEntry<K, V>>
                ): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        var found = false
                        for (entry in elements) {
                            if (entry.key == this@MutableScatterMap.keys[index] &&
                                entry.value == this@MutableScatterMap.values[index]
                            ) {
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            removeValueAt(index)
                            changed = true
                        }
                    }
                    return changed
                }

                override fun removeAll(
                    elements: Collection<MutableMap.MutableEntry<K, V>>
                ): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        for (entry in elements) {
                            if (entry.key == this@MutableScatterMap.keys[index] &&
                                entry.value == this@MutableScatterMap.values[index]
                            ) {
                                removeValueAt(index)
                                changed = true
                                break
                            }
                        }
                    }
                    return changed
                }

                override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                    val index = findKeyIndex(element.key)
                    if (index >= 0 && this@MutableScatterMap.values[index] == element.value) {
                        removeValueAt(index)
                        return true
                    }
                    return false
                }
            }

        override val keys: MutableSet<K>
            get() = object : MutableSet<K> {
                override val size: Int get() = this@MutableScatterMap._size

                override fun isEmpty(): Boolean = this@MutableScatterMap.isEmpty()

                override fun iterator(): MutableIterator<K> = object : MutableIterator<K> {
                    private val iterator = iterator {
                        this@MutableScatterMap.forEachIndexed { index ->
                            yield(index)
                        }
                    }
                    private var current: Int = -1

                    override fun hasNext(): Boolean = iterator.hasNext()

                    override fun next(): K {
                        current = iterator.next()
                        @Suppress("UNCHECKED_CAST")
                        return this@MutableScatterMap.keys[current] as K
                    }

                    override fun remove() {
                        if (current >= 0) {
                            this@MutableScatterMap.removeValueAt(current)
                            current = -1
                        }
                    }
                }

                override fun clear() {
                    this@MutableScatterMap.clear()
                }

                override fun addAll(elements: Collection<K>): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun add(element: K): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun retainAll(elements: Collection<K>): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        if (this@MutableScatterMap.keys[index] !in elements) {
                            removeValueAt(index)
                            changed = true
                        }
                    }
                    return changed
                }

                override fun removeAll(elements: Collection<K>): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        if (this@MutableScatterMap.keys[index] in elements) {
                            removeValueAt(index)
                            changed = true
                        }
                    }
                    return changed
                }

                override fun remove(element: K): Boolean {
                    val index = findKeyIndex(element)
                    if (index >= 0) {
                        removeValueAt(index)
                        return true
                    }
                    return false
                }

                override fun containsAll(elements: Collection<K>): Boolean =
                    elements.all { this@MutableScatterMap.containsKey(it) }

                override fun contains(element: K): Boolean =
                    this@MutableScatterMap.containsKey(element)
            }

        override val values: MutableCollection<V>
            get() = object : MutableCollection<V> {
                override val size: Int get() = this@MutableScatterMap._size

                override fun isEmpty(): Boolean = this@MutableScatterMap.isEmpty()

                override fun iterator(): MutableIterator<V> = object : MutableIterator<V> {
                    private val iterator = iterator {
                        this@MutableScatterMap.forEachIndexed { index ->
                            yield(index)
                        }
                    }
                    private var current: Int = -1

                    override fun hasNext(): Boolean = iterator.hasNext()

                    override fun next(): V {
                        current = iterator.next()
                        @Suppress("UNCHECKED_CAST")
                        return this@MutableScatterMap.values[current] as V
                    }

                    override fun remove() {
                        if (current >= 0) {
                            this@MutableScatterMap.removeValueAt(current)
                            current = -1
                        }
                    }
                }

                override fun clear() {
                    this@MutableScatterMap.clear()
                }

                override fun addAll(elements: Collection<V>): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun add(element: V): Boolean {
                    throw UnsupportedOperationException()
                }

                override fun retainAll(elements: Collection<V>): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        if (this@MutableScatterMap.values[index] !in elements) {
                            removeValueAt(index)
                            changed = true
                        }
                    }
                    return changed
                }

                override fun removeAll(elements: Collection<V>): Boolean {
                    var changed = false
                    this@MutableScatterMap.forEachIndexed { index ->
                        if (this@MutableScatterMap.values[index] in elements) {
                            removeValueAt(index)
                            changed = true
                        }
                    }
                    return changed
                }

                override fun remove(element: V): Boolean {
                    this@MutableScatterMap.forEachIndexed { index ->
                        if (this@MutableScatterMap.values[index] == element) {
                            removeValueAt(index)
                            return true
                        }
                    }
                    return false
                }

                override fun containsAll(elements: Collection<V>): Boolean =
                    elements.all { this@MutableScatterMap.containsValue(it) }

                override fun contains(element: V): Boolean =
                    this@MutableScatterMap.containsValue(element)
            }

        override fun clear() {
            this@MutableScatterMap.clear()
        }

        override fun remove(key: K): V? = this@MutableScatterMap.remove(key)

        override fun putAll(from: Map<out K, V>) {
            from.forEach { (key, value) ->
                this[key] = value
            }
        }

        override fun put(key: K, value: V): V? = this@MutableScatterMap.put(key, value)
    }
}

private class MapEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>

private class MutableMapEntry<K, V>(
    val keys: Array<Any?>,
    val values: Array<Any?>,
    val index: Int
) : MutableMap.MutableEntry<K, V> {

    @Suppress("UNCHECKED_CAST")
    override fun setValue(newValue: V): V {
        val oldValue = values[index]
        values[index] = newValue
        return oldValue as V
    }

    @Suppress("UNCHECKED_CAST")
    override val key: K get() = keys[index] as K

    @Suppress("UNCHECKED_CAST")
    override val value: V get() = values[index] as V
}

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
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

public actual sealed class ScatterSet<E> {
    // NOTE: Our arrays are marked internal to implement inlined forEach{}
    // The backing array for the metadata bytes contains
    // `capacity + 1 + ClonedMetadataCount` elements, including when
    // the set is empty (see [EmptyGroup]).
    @PublishedApi
    @JvmField
    internal var metadata: LongArray = EmptyGroup

    @PublishedApi
    @JvmField
    internal var elements: Array<Any?> = EMPTY_OBJECTS

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the capacity
    @JvmField
    internal var _capacity: Int = 0

    @get:androidx.annotation.IntRange(from = 0)
    public actual val capacity: Int
        get() = _capacity

    // We use a backing field for capacity to avoid invokevirtual calls
    // every time we need to look at the size
    @JvmField
    internal var _size: Int = 0

    @get:androidx.annotation.IntRange(from = 0)
    public actual val size: Int
        get() = _size

    public actual fun any(): Boolean = _size != 0

    public actual fun none(): Boolean = _size == 0

    public actual fun isEmpty(): Boolean = _size == 0

    public actual fun isNotEmpty(): Boolean = _size != 0

    public actual inline fun first(): E {
        forEach { return it }
        throw NoSuchElementException("The ScatterSet is empty")
    }

    public actual inline fun first(predicate: (element: E) -> Boolean): E {
        contract { callsInPlace(predicate) }
        forEach { if (predicate(it)) return it }
        throw NoSuchElementException("Could not find a match")
    }

    public actual inline fun firstOrNull(predicate: (element: E) -> Boolean): E? {
        contract { callsInPlace(predicate) }
        forEach { if (predicate(it)) return it }
        return null
    }

    /**
     * Iterates over every element stored in this set by invoking
     * the specified [block] lambda.
     */
    @PublishedApi
    internal inline fun forEachIndex(block: (index: Int) -> Unit) {
        contract { callsInPlace(block) }
        val m = metadata
        val lastIndex = m.size - 2 // We always have 0 or at least 2 elements

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

    public actual inline fun forEach(block: (element: E) -> Unit) {
        contract { callsInPlace(block) }
        val k = elements

        forEachIndex { index ->
            @Suppress("UNCHECKED_CAST")
            block(k[index] as E)
        }
    }

    public actual inline fun all(predicate: (element: E) -> Boolean): Boolean {
        contract { callsInPlace(predicate) }
        forEach { element ->
            if (!predicate(element)) return false
        }
        return true
    }

    public actual inline fun any(predicate: (element: E) -> Boolean): Boolean {
        contract { callsInPlace(predicate) }
        forEach { element ->
            if (predicate(element)) return true
        }
        return false
    }

    @androidx.annotation.IntRange(from = 0)
    public actual fun count(): Int = size

    @androidx.annotation.IntRange(from = 0)
    public actual inline fun count(predicate: (element: E) -> Boolean): Int {
        contract { callsInPlace(predicate) }
        var count = 0
        forEach { element ->
            if (predicate(element)) count++
        }
        return count
    }

    public actual operator fun contains(element: E): Boolean = findElementIndex(element) >= 0

    @JvmOverloads
    public actual fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((E) -> CharSequence)?,
    ): String = buildString {
        append(prefix)
        var index = 0
        this@ScatterSet.forEach { element ->
            if (index == limit) {
                append(truncated)
                return@buildString
            }
            if (index != 0) {
                append(separator)
            }
            if (transform == null) {
                append(element)
            } else {
                append(transform(element))
            }
            index++
        }
        append(postfix)
    }

    /**
     * Returns the hash code value for this set. The hash code of a set is defined to
     * be the sum of the hash codes of the elements in the set, where the hash code
     * of a null element is defined to be zero
     */
    public override fun hashCode(): Int {
        var hash = 0

        forEach { element ->
            hash += element.hashCode()
        }

        return hash
    }

    /**
     * Compares the specified object [other] with this hash set for equality.
     * The two objects are considered equal if [other]:
     * - Is a [ScatterSet]
     * - Has the same [size] as this set
     * - Contains elements equal to this set's elements
     */
    public override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is ScatterSet<*>) {
            return false
        }
        if (other.size != size) {
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val o = other as ScatterSet<Any?>

        forEach { element ->
            if (element !in o) {
                return false
            }
        }

        return true
    }

    /**
     * Returns a string representation of this set. The set is denoted in the
     * string by the `[]`. Each element is separated by `, `.
     */
    override fun toString(): String = joinToString(prefix = "[", postfix = "]") { element ->
        if (element === this) {
            "(this)"
        } else {
            element.toString()
        }
    }

    /**
     * Scans the set to find the index in the backing arrays of the
     * specified [element]. Returns -1 if the element is not present.
     */
    internal inline fun findElementIndex(element: E): Int {
        val hash = hash(element)
        val hash2 = h2(hash)

        val probeMask = _capacity
        var probeOffset = h1(hash) and probeMask
        var probeIndex = 0
        while (true) {
            val g = group(metadata, probeOffset)
            var m = g.match(hash2)
            while (m.hasNext()) {
                val index = (probeOffset + m.get()) and probeMask
                if (elements[index] == element) {
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

    public actual fun asSet(): Set<E> = SetWrapper()

    internal open inner class SetWrapper : Set<E> {
        override val size: Int get() = this@ScatterSet._size
        override fun containsAll(elements: Collection<E>): Boolean {
            elements.forEach { element ->
                if (!this@ScatterSet.contains(element)) {
                    return false
                }
            }
            return true
        }

        @Suppress("KotlinOperator")
        override fun contains(element: E): Boolean {
            return this@ScatterSet.contains(element)
        }

        override fun isEmpty(): Boolean = this@ScatterSet.isEmpty()
        override fun iterator(): Iterator<E> {
            return iterator {
                this@ScatterSet.forEach { element ->
                    yield(element)
                }
            }
        }
    }
}

public actual class MutableScatterSet<E> actual constructor(
    initialCapacity: Int,
) : ScatterSet<E>() {
    // Number of elements we can add before we need to grow
    private var growthLimit = 0

    init {
        require(initialCapacity >= 0) { "Capacity must be a positive value." }
        initializeStorage(unloadedCapacity(initialCapacity))
    }

    private fun initializeStorage(initialCapacity: Int) {
        val newCapacity = if (initialCapacity > 0) {
            // Since we use longs for storage, our capacity is never < 7, enforce
            // it here. We do have a special case for 0 to create small empty maps
            maxOf(7, normalizeCapacity(initialCapacity))
        } else {
            0
        }
        _capacity = newCapacity
        initializeMetadata(newCapacity)
        elements = arrayOfNulls(newCapacity)
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

    public actual fun add(element: E): Boolean {
        val oldSize = size
        val index = findAbsoluteInsertIndex(element)
        elements[index] = element
        return size != oldSize
    }

    public actual operator fun plusAssign(element: E) {
        val index = findAbsoluteInsertIndex(element)
        elements[index] = element
    }

    public actual fun addAll(@Suppress("ArrayReturn") elements: Array<out E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual fun addAll(elements: Iterable<E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual fun addAll(elements: Sequence<E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual fun addAll(elements: ScatterSet<E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual fun addAll(elements: ObjectList<E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual operator fun plusAssign(@Suppress("ArrayReturn") elements: Array<out E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual operator fun plusAssign(elements: Iterable<E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual operator fun plusAssign(elements: Sequence<E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual operator fun plusAssign(elements: ScatterSet<E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual operator fun plusAssign(elements: ObjectList<E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual fun remove(element: E): Boolean {
        val index = findElementIndex(element)
        val exists = index >= 0
        if (exists) {
            removeElementAt(index)
        }
        return exists
    }

    public actual operator fun minusAssign(element: E) {
        val index = findElementIndex(element)
        if (index >= 0) {
            removeElementAt(index)
        }
    }

    public actual fun removeAll(@Suppress("ArrayReturn") elements: Array<out E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual fun removeAll(elements: Sequence<E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual fun removeAll(elements: Iterable<E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual fun removeAll(elements: ScatterSet<E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual fun removeAll(elements: ObjectList<E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual operator fun minusAssign(@Suppress("ArrayReturn") elements: Array<out E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual operator fun minusAssign(elements: Sequence<E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual operator fun minusAssign(elements: Iterable<E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual operator fun minusAssign(elements: ScatterSet<E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual operator fun minusAssign(elements: ObjectList<E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual inline fun removeIf(predicate: (E) -> Boolean) {
        val elements = elements
        forEachIndex { index ->
            @Suppress("UNCHECKED_CAST")
            if (predicate(elements[index] as E)) {
                removeElementAt(index)
            }
        }
    }

    @PublishedApi
    internal fun removeElementAt(index: Int) {
        _size -= 1

        // TODO: We could just mark the element as empty if there's a group
        //       window around this element that was already empty
        writeMetadata(index, Deleted)
        elements[index] = null
    }

    public actual fun clear() {
        _size = 0
        if (metadata !== EmptyGroup) {
            metadata.fill(AllEmpty)
            writeRawMetadata(metadata, _capacity, Sentinel)
        }
        elements.fill(null, 0, _capacity)
        initializeGrowth()
    }

    /**
     * Scans the set to find the index at which we can store the given [element].
     * If the element already exists in the set, its index
     * will be returned, otherwise the index of an empty slot will be returned.
     * Calling this function may cause the internal storage to be reallocated
     * if the set is full.
     */
    private fun findAbsoluteInsertIndex(element: E): Int {
        val hash = hash(element)
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
                if (elements[index] == element) {
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

        return index
    }

    /**
     * Finds the first empty or deleted slot in the set in which we can
     * store an element without resizing the internal storage.
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

    @androidx.annotation.IntRange(from = 0)
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
     * remove deleted elements from the set to avoid an expensive reallocation
     * of the underlying storage. This "rehash in place" occurs when the
     * current size is <= 25/32 of the set capacity. The choice of 25/32 is
     * detailed in the implementation of abseil's `raw_hash_map`.
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
        val previousElements = elements
        val previousCapacity = _capacity

        initializeStorage(newCapacity)

        val newElements = elements

        for (i in 0 until previousCapacity) {
            if (isFull(previousMetadata, i)) {
                val previousElement = previousElements[i]
                val hash = hash(previousElement)
                val index = findFirstAvailableSlot(h1(hash))

                writeMetadata(index, h2(hash).toLong())
                newElements[index] = previousElement
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

    public actual fun asMutableSet(): MutableSet<E> = MutableSetWrapper()

    private inner class MutableSetWrapper : SetWrapper(), MutableSet<E> {
        override fun add(element: E): Boolean = this@MutableScatterSet.add(element)

        override fun addAll(elements: Collection<E>): Boolean =
            this@MutableScatterSet.addAll(elements)

        override fun clear() {
            this@MutableScatterSet.clear()
        }

        override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
            var current = -1
            val iterator = iterator<E> {
                this@MutableScatterSet.forEachIndex { index ->
                    current = index
                    @Suppress("UNCHECKED_CAST")
                    yield(elements[index] as E)
                }
            }

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): E = iterator.next()

            override fun remove() {
                if (current != -1) {
                    this@MutableScatterSet.removeElementAt(current)
                    current = -1
                }
            }
        }

        override fun remove(element: E): Boolean = this@MutableScatterSet.remove(element)

        override fun retainAll(elements: Collection<E>): Boolean {
            var changed = false
            this@MutableScatterSet.forEachIndex { index ->
                @Suppress("UNCHECKED_CAST")
                val element = this@MutableScatterSet.elements[index] as E
                if (element !in elements) {
                    this@MutableScatterSet.removeElementAt(index)
                    changed = true
                }
            }
            return changed
        }

        override fun removeAll(elements: Collection<E>): Boolean {
            val oldSize = this@MutableScatterSet.size
            for (element in elements) {
                this@MutableScatterSet -= element
            }
            return oldSize != this@MutableScatterSet.size
        }
    }
}

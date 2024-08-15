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

import kotlin.contracts.contract

public actual sealed class ScatterSet<E> protected constructor(
    private val delegate: Set<E>,
) {
    @get:androidx.annotation.IntRange(from = 0)
    public actual val capacity: Int
        get() = 0

    @get:androidx.annotation.IntRange(from = 0)
    public actual val size: Int
        get() = delegate.size

    public actual fun any(): Boolean = size != 0

    public actual fun none(): Boolean = size == 0

    public actual fun isEmpty(): Boolean = size == 0

    public actual fun isNotEmpty(): Boolean = size != 0

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

    public actual inline fun forEach(block: (element: E) -> Unit) {
        asSet().forEach {
            block(it)
        }
    }

    public actual inline fun all(predicate: (element: E) -> Boolean): Boolean {
        return asSet().all(predicate)
    }

    public actual inline fun any(predicate: (element: E) -> Boolean): Boolean {
        return asSet().any(predicate)
    }

    @androidx.annotation.IntRange(from = 0)
    public actual fun count(): Int = size

    @androidx.annotation.IntRange(from = 0)
    public actual inline fun count(predicate: (element: E) -> Boolean): Int {
        return asSet().count(predicate)
    }

    public actual operator fun contains(element: E): Boolean = asSet().contains(element)

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

    public override fun hashCode(): Int {
        return asSet().hashCode()
    }

    public override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        return other is ScatterSet<*> && delegate == other.delegate
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

    public actual fun asSet(): Set<E> = delegate
}

public actual class MutableScatterSet<E> internal constructor(
    private val mutableDelegate: MutableSet<E>,
) : ScatterSet<E>(mutableDelegate) {

    actual constructor(
        initialCapacity: Int
    ) : this(mutableSetOf())

    public actual fun add(element: E): Boolean {
        return mutableDelegate.add(element)
    }

    public actual operator fun plusAssign(element: E) {
        return mutableDelegate.plusAssign(element)
    }

    public actual fun addAll(@Suppress("ArrayReturn") elements: Array<out E>): Boolean {
        return mutableDelegate.addAll(elements)
    }

    public actual fun addAll(elements: Iterable<E>): Boolean {
        return mutableDelegate.addAll(elements)
    }

    public actual fun addAll(elements: Sequence<E>): Boolean {
        return mutableDelegate.addAll(elements)
    }

    public actual fun addAll(elements: ScatterSet<E>): Boolean {
        return mutableDelegate.addAll(elements.asSet())
    }

    public actual fun addAll(elements: ObjectList<E>): Boolean {
        val oldSize = size
        plusAssign(elements)
        return oldSize != size
    }

    public actual operator fun plusAssign(@Suppress("ArrayReturn") elements: Array<out E>) {
        mutableDelegate.plusAssign(elements)
    }

    public actual operator fun plusAssign(elements: Iterable<E>) {
        mutableDelegate.plusAssign(elements)
    }

    public actual operator fun plusAssign(elements: Sequence<E>) {
        mutableDelegate.plusAssign(elements)
    }

    public actual operator fun plusAssign(elements: ScatterSet<E>) {
        mutableDelegate.plusAssign(elements.asSet())
    }

    public actual operator fun plusAssign(elements: ObjectList<E>) {
        elements.forEach { element ->
            plusAssign(element)
        }
    }

    public actual fun remove(element: E): Boolean {
        return mutableDelegate.remove(element)
    }

    public actual operator fun minusAssign(element: E) {
        mutableDelegate.minusAssign(element)
    }

    public actual fun removeAll(@Suppress("ArrayReturn") elements: Array<out E>): Boolean {
        return mutableDelegate.removeAll(elements)
    }

    public actual fun removeAll(elements: Sequence<E>): Boolean {
        return mutableDelegate.removeAll(elements)
    }

    public actual fun removeAll(elements: Iterable<E>): Boolean {
        return mutableDelegate.removeAll(elements)
    }

    public actual fun removeAll(elements: ScatterSet<E>): Boolean {
        return mutableDelegate.removeAll(elements.asSet())
    }

    public actual fun removeAll(elements: ObjectList<E>): Boolean {
        val oldSize = size
        minusAssign(elements)
        return oldSize != size
    }

    public actual operator fun minusAssign(@Suppress("ArrayReturn") elements: Array<out E>) {
        mutableDelegate.minusAssign(elements)
    }

    public actual operator fun minusAssign(elements: Sequence<E>) {
        mutableDelegate.minusAssign(elements)
    }

    public actual operator fun minusAssign(elements: Iterable<E>) {
        mutableDelegate.minusAssign(elements)
    }

    public actual operator fun minusAssign(elements: ScatterSet<E>) {
        mutableDelegate.minusAssign(elements.asSet())
    }

    public actual operator fun minusAssign(elements: ObjectList<E>) {
        elements.forEach { element ->
            minusAssign(element)
        }
    }

    public actual inline fun removeIf(predicate: (E) -> Boolean) {
        val i = asMutableSet().iterator()
        while (i.hasNext()) {
            if (predicate(i.next())) {
                i.remove()
            }
        }
    }

    public actual fun clear() {
        mutableDelegate.clear()
    }

    @androidx.annotation.IntRange(from = 0)
    public actual fun trim(): Int {
        return 0
    }

    public actual fun asMutableSet(): MutableSet<E> = mutableDelegate
}

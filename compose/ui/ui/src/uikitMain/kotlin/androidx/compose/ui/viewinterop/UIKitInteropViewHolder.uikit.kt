/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.viewinterop

import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.UIKit.UIView

internal class UIKitInteropViewHolder<T : UIView>(
    factory: () -> T,
    interopContainer: InteropContainer,
    properties: UIKitInteropProperties,
    callbacks: UIKitInteropCallbacks<T>?,
    compositeKeyHash: Int,
) : UIKitInteropElementHolder<T>(
    factory,
    interopContainer,
    properties,
    callbacks,
    compositeKeyHash
) {
    init {
        // Group will be placed to hierarchy in [InteropContainer.placeInteropView]
        group.addSubview(typedInteropView)
    }

    override fun setUserComponentFrame(rect: CValue<CGRect>) {
        typedInteropView.setFrame(rect)

        super.setUserComponentFrame(rect)
    }

    override fun insertInteropView(root: InteropViewGroup, index: Int) {
        root.insertSubview(group, index.toLong())

        super.insertInteropView(root, index)
    }

    override fun removeInteropView(root: InteropViewGroup) {
        group.removeFromSuperview()

        super.removeInteropView(root)
    }
}
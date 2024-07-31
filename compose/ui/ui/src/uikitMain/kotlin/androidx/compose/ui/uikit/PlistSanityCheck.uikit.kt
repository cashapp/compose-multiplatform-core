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

package androidx.compose.ui.uikit

import kotlinx.atomicfu.atomic
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber
import platform.darwin.DISPATCH_QUEUE_PRIORITY_LOW
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

internal object PlistSanityCheck {
    private val isPerformed = atomic(false)

    fun performIfNeeded(enforceStrictPlistSanityCheck: Boolean) {
        if (isPerformed.compareAndSet(expect = false, update = true)) {
            dispatch_async(dispatch_get_global_queue(
                DISPATCH_QUEUE_PRIORITY_LOW.toLong(),
                0u
            )) {
                val entry = NSBundle
                    .mainBundle
                    .objectForInfoDictionaryKey("CADisableMinimumFrameDurationOnPhone") as? NSNumber

                if (entry?.boolValue != true) {
                    if (enforceStrictPlistSanityCheck) {
                        val message = """
                            Error: `Info.plist` doesn't have a valid `CADisableMinimumFrameDurationOnPhone` entry. 
                            This will result in an inadequate performance on devices with high refresh rate. 
                            Add `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` entry to `Info.plist` to fix this error. 
                            If you don't have a separate plist file, add the entry to the target from within Xcode: Project -> Targets -> Info -> Custom iOS Target Properties.                            
                            Or set `ComposeUIViewControllerConfiguration.enforceStrictPlistSanityCheck` to false, if it's intended.                            
                        """.trimIndent()
                        println(message)
                        error(message)
                    } else {
                        println("Warning: `Info.plist` doesn't have a valid `CADisableMinimumFrameDurationOnPhone` entry. Framerate will be restricted to 60hz on iPhones. To support high frequency rendering on iPhones, add `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` entry to `Info.plist`.")
                    }
                }
            }
        }
    }
}
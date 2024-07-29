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

package androidx.compose.ui.platform

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions

actual interface PlatformTextInputMethodRequest {
    val state: TextFieldStateAdapter
    val imeOptions: ImeOptions
    val onEditCommand: (List<EditCommand>) -> Unit
    val onImeAction: ((ImeAction) -> Unit)?
}

/**
 * The purpose of this interface is to provide, in the `ui` module, an adapter for
 * `TransformedTextFieldStateAdapter`, which is in the `foundation` module.
 *
 * It exposes all the properties of `TransformedTextFieldStateAdapter` that are necessary for
 * implementing input methods.
 */
interface TextFieldStateAdapter {
    val text: CharSequence
    val selection: TextRange
    val composition: TextRange?
}

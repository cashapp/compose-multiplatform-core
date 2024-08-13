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

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropCallbacks
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSize
import platform.Foundation.NSSelectorFromString
import platform.MapKit.MKMapView
import platform.UIKit.*

private class BlueViewController: UIViewController(nibName = null, bundle = null) {
    val label = UILabel()

    override fun loadView() {
        setView(label)
    }

    override fun viewDidLoad() {
        super.viewDidLoad()

        label.textAlignment = NSTextAlignmentCenter
        label.textColor = UIColor.whiteColor
        label.backgroundColor = UIColor.blueColor
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)

        println("viewWillAppear animated=$animated")
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)

        println("viewDidAppear animated=$animated")
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)

        println("viewDidDisappear animated=$animated")
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)

        println("viewWillDisappear animated=$animated")
    }
}

private class TouchReactingView: UIView(frame = CGRectZero.readValue()) {
    init {
        setUserInteractionEnabled(true)

        setDefaultColor()
    }

    private fun setDefaultColor() {
        backgroundColor = UIColor.greenColor
    }

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesBegan(touches, withEvent)
        backgroundColor = UIColor.redColor
    }

    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesMoved(touches, withEvent)
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesEnded(touches, withEvent)
        setDefaultColor()
    }

    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent?) {
        super.touchesCancelled(touches, withEvent)
        setDefaultColor()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
val UIKitInteropExample = Screen.Example("UIKitInterop") {
    var text by remember { mutableStateOf("Type something") }
    var updatedValue by remember { mutableStateOf(null as Offset?) }


    LazyColumn(Modifier.fillMaxSize()) {
        item {
            UIKitView(
                factory = {
                    MKMapView()
                },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                update = {
                    println("MKMapView updated")
                },
                callbacks = object : UIKitInteropCallbacks<MKMapView> {
                    override fun onDidAppear(component: MKMapView) {
                        println("onDidAppear frame: ${NSStringFromCGRect(component.frame)}, isAttached = ${component.window != null}")
                    }

                    override fun onDidDisappear(component: MKMapView) {
                        println("onDidDisappear frame: ${NSStringFromCGRect(component.frame)}, isAttached = ${component.window != null}")
                    }

                    override fun onWillAppear(component: MKMapView) {
                        println("onWillAppear frame: ${NSStringFromCGRect(component.frame)}, isAttached = ${component.window != null}")
                    }

                    override fun onWillDisappear(component: MKMapView) {
                        println("onWillDisappear frame: ${NSStringFromCGRect(component.frame)}, isAttached = ${component.window != null}")
                    }

                    override fun onResize(component: MKMapView, size: CValue<CGSize>) {
                        println("onResize frame: ${NSStringFromCGRect(component.frame)}")
                    }
                }
            )
        }
        item {
            UIKitViewController(
                factory = {
                    BlueViewController()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .onGloballyPositioned { coordinates ->
                        val rootCoordinates = coordinates.findRootCoordinates()
                        val box = rootCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
                        updatedValue = box.topLeft
                    },
                update = { viewController ->
                    updatedValue?.let {
                        viewController.label.text = "${it.x}, ${it.y}"
                    }
                },
                properties = UIKitInteropProperties(
                    interactionMode = null,
                    isNativeAccessibilityEnabled = false
                )
            )
        }
        items(100) { index ->
            when (index % 5) {
                0 -> Text("material.Text $index", Modifier.fillMaxSize().height(40.dp))
                1 -> UIKitView(
                    factory = {
                        val label = UILabel(frame = CGRectZero.readValue())
                        label.text = "UILabel $index"
                        label.textColor = UIColor.blackColor
                        label
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    properties = UIKitInteropProperties(
                        interactionMode = null
                    )
                )
                2 -> UIKitView(
                    factory = { TouchReactingView() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
                3 -> TextField(text, onValueChange = { text = it }, Modifier.fillMaxWidth())
                4 -> ComposeUITextField(text, onValueChange = { text = it }, Modifier.fillMaxWidth().height(40.dp))
            }
        }
    }
}

/**
 * Compose wrapper for native UITextField.
 * @param value the input text to be shown in the text field.
 * @param onValueChange the callback that is triggered when the input service updates the text. An
 * updated text comes as a parameter of the callback
 * @param modifier a [Modifier] for this text field. Size should be specified in modifier.
 */
@Composable
private fun ComposeUITextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    val latestOnValueChanged by rememberUpdatedState(onValueChange)

    UIKitView(
        factory = {
            val textField = object : UITextField(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                @ObjCAction
                fun editingChanged() {
                    latestOnValueChanged(text ?: "")
                }
            }
            textField.addTarget(
                target = textField,
                action = NSSelectorFromString(textField::editingChanged.name),
                forControlEvents = UIControlEventEditingChanged
            )
            textField
        },
        modifier = modifier,
        update = { textField ->
            println("Update called for UITextField(0x${textField.objcPtr().toLong().toString(16)}, value = $value")
            textField.text = value
        }
    )
}

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

package androidx.compose.ui.window

import androidx.compose.ui.platform.CUPERTINO_TOUCH_SLOP
import androidx.compose.ui.uikit.utils.CMPGestureRecognizer
import androidx.compose.ui.uikit.utils.CMPGestureRecognizerHandlerProtocol
import androidx.compose.ui.viewinterop.InteropView
import androidx.compose.ui.viewinterop.InteropWrappingView
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIEvent
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerState
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIGestureRecognizerStatePossible
import platform.UIKit.UIPress
import platform.UIKit.UIPressesEvent
import platform.UIKit.UITouch
import platform.UIKit.UITouchPhase
import platform.UIKit.UIView
import platform.UIKit.setState
import platform.darwin.NSObject

/**
 * Subset of [UITouchPhase] reflecting immediate phase when event is received by the [UIView] or
 * [UIGestureRecognizer].
 */
internal enum class CupertinoTouchesPhase {
    BEGAN, MOVED, ENDED, CANCELLED
}

private val UIGestureRecognizerState.isOngoing: Boolean
    get() =
        when (this) {
            UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> true
            else -> false
        }

/**
 * Enum class representing the possible hit test result of [InteractionUIViewHitTestResult].
 * This enum is used solely to determine the strategy of touch event delivery and
 * doesn't require any additional information about the hit-tested view itself.
 */
private sealed interface InteractionUIViewHitTestResult {
    data object Self : InteractionUIViewHitTestResult

    data object NonCooperativeChildView : InteractionUIViewHitTestResult

    class CooperativeChildView(
        val delay: Int
    ) : InteractionUIViewHitTestResult
}

/**
 * Implementation of [CMPGestureRecognizer] that handles touch events and forwards
 * them. The main difference from the original [UIView] touches based is that it's built on top of
 * [CMPGestureRecognizer], which play differently with UIKit touches processing and are required
 * for the correct handling of the touch events in interop scenarios, because they rely on
 * [UIGestureRecognizer] failure requirements and touches interception, which is an exclusive way
 * to control touches delivery to [UIView]s and their [UIGestureRecognizer]s in a fine-grain manner.
 */
private class GestureRecognizerHandlerImpl(
    private var onTouchesEvent: (view: UIView, touches: Set<*>, event: UIEvent?, phase: CupertinoTouchesPhase) -> Unit,
    private var view: UIView?,
    private val onTouchesCountChanged: (by: Int) -> Unit,
) : NSObject(), CMPGestureRecognizerHandlerProtocol {
    /**
     * The actual view that was hit-tested by the first touch in the sequence.
     * It could be interop view, for example. If there are tracked touches, assignment is ignored.
     */
    var hitTestResult: InteractionUIViewHitTestResult? = null
        set(value) {
            /**
             * Only remember the first hit-tested view in the sequence.
             */
            if (initialLocation == null) {
                field = value
            }
        }

    /**
     * [CMPGestureRecognizer] that is associated with this handler.
     */
    var gestureRecognizer: CMPGestureRecognizer? = null

    private var gestureRecognizerState: UIGestureRecognizerState
        get() = gestureRecognizer?.state ?: UIGestureRecognizerStateFailed
        set(value) {
            gestureRecognizer?.setState(value)
        }

    /**
     * Initial centroid location in the sequence to measure the motion slop and to determine whether the gesture
     * should be recognized or failed and pass touches to interop views.
     */
    private var initialLocation: CValue<CGPoint>? = null

    /**
     * Touches that are currently tracked by the gesture recognizer.
     */
    private val trackedTouches: MutableSet<UITouch> = mutableSetOf()

    /**
     * Checks whether the centroid location of [trackedTouches] has exceeded the scrolling slop
     * relative to [initialLocation]
     */
    private val isLocationDeltaAboveSlop: Boolean
        get() {
            val initialLocation = initialLocation ?: return false
            val centroidLocation = trackedTouchesCentroidLocation ?: return false

            val slop = CUPERTINO_TOUCH_SLOP.toDouble()

            val dx = centroidLocation.useContents { x - initialLocation.useContents { x } }
            val dy = centroidLocation.useContents { y - initialLocation.useContents { y } }

            return dx * dx + dy * dy > slop * slop
        }

    /**
     * Calculates the centroid of the tracked touches.
     */
    private val trackedTouchesCentroidLocation: CValue<CGPoint>?
        get() {
            if (trackedTouches.isEmpty()) {
                return null
            }

            var centroidX = 0.0
            var centroidY = 0.0

            for (touch in trackedTouches) {
                val location = touch.locationInView(view)
                location.useContents {
                    centroidX += x
                    centroidY += y
                }
            }

            return CGPointMake(
                x = centroidX / trackedTouches.size.toDouble(),
                y = centroidY / trackedTouches.size.toDouble()
            )
        }

    /**
     * Implementation of [CMPGestureRecognizerHandlerProtocol] that handles touchesBegan event and
     * forwards it here.
     *
     * There are following scenarios:
     * 1. Those are first touches in the sequence, the interaction view is hit-tested. In this case, we
     * should start the gesture recognizer immediately and start passing touches to the Compose
     * runtime.
     *
     * 2. Those are first touches in the sequence, an interop view is hit-tested. In this case we
     * intercept touches from interop views until the gesture recognizer is explicitly failed.
     * See [UIGestureRecognizer.delaysTouchesBegan]. In the same time we schedule a failure in
     * [CMPGestureRecognizer.scheduleFailure], which will pass intercepted touches to the interop
     * views if the gesture recognizer is not recognized within a certain time frame
     * (UIScrollView reverse-engineered 150ms is used).
     * The similar approach is used by [UIScrollView](https://developer.apple.com/documentation/uikit/uiscrollview)
     *
     * 3. Those are not the first touches in the sequence. A gesture is recognized.
     * We should continue with scenario (1), we don't yet support multitouch sequence in
     * compose and interop view simultaneously (e.g. scrolling native horizontal
     * scroll and compose horizontal scroll with different fingers)
     *
     * 4. Those are not the first touches in the sequence. A gesture is not recognized.
     * See if centroid of the tracked touches has moved enough to recognize the gesture.
     *
     * TODO (not yet tracked):
     *  An improvement to the current implementation would be to remove the delay if hitTest
     *  on ComposeScene didn't go through a node, that has a PointerFilter attached
     *  (e.g. a scrollable)
     *
     */
    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        if (hitTestResult == InteractionUIViewHitTestResult.NonCooperativeChildView) {
            // If child view doesn't want delay logic applied, we should immediately fail the gesture
            // and allow touches to go through directly to that view, gesture recognizer should
            // fail immediately, no touches will be received by Compose and the gesture recognizer after
            // this point until all fingers are lifted.
            gestureRecognizerState = UIGestureRecognizerStateFailed
            return
        }

        val areTouchesInitial = startTrackingTouches(touches)

        onTouchesEvent(trackedTouches, withEvent, CupertinoTouchesPhase.BEGAN)

        if (gestureRecognizerState.isOngoing || hitTestResult == InteractionUIViewHitTestResult.Self) {
            // Golden path, immediately start/continue the gesture recognizer if possible and pass touches.
            when (gestureRecognizerState) {
                UIGestureRecognizerStatePossible -> {
                    gestureRecognizerState = UIGestureRecognizerStateBegan
                }

                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    gestureRecognizerState = UIGestureRecognizerStateChanged
                }
            }
        } else {
            if (areTouchesInitial) {
                // We are in the scenario (2), we should schedule failure and pass touches to the
                // interop view.
                gestureRecognizer?.scheduleFailure()
            } else {
                // We are in the scenario (4), check if the gesture recognizer should be recognized.
                checkPanIntent()
            }
        }
    }

    /**
     * Implementation of [CMPGestureRecognizerHandlerProtocol] that handles touchesMoved event and
     * forwards it here.
     *
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized.
     * In this case, we should just forward the touches.
     *
     * 2. An interop view is hit-tested. In this case we should check if the pan intent is met.
     */
    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        onTouchesEvent(trackedTouches, withEvent, CupertinoTouchesPhase.MOVED)

        if (gestureRecognizerState.isOngoing || hitTestResult == InteractionUIViewHitTestResult.Self) {
            // Golden path, just update the gesture recognizer state and pass touches to
            // the Compose runtime.

            when (gestureRecognizerState) {
                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    gestureRecognizerState = UIGestureRecognizerStateChanged
                }
            }
        } else {
            checkPanIntent()
        }
    }

    /**
     * Implementation of [CMPGestureRecognizerHandlerProtocol] that handles touchesEnded event and
     * forwards it here.
     *
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized. Just update the gesture
     * recognizer state and pass touches to the Compose runtime.
     *
     * 2. An interop view is hit-tested. In this case if there are no tracked touches left -
     * we need to allow all the touches to be passed to the interop view by failing explicitly.
     */
    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        onTouchesEvent(trackedTouches, withEvent, CupertinoTouchesPhase.ENDED)

        stopTrackingTouches(touches)

        if (gestureRecognizerState.isOngoing || hitTestResult == InteractionUIViewHitTestResult.Self) {
            // Golden path, just update the gesture recognizer state and pass touches to
            // the Compose runtime.

            if (gestureRecognizerState.isOngoing) {
                gestureRecognizerState = if (trackedTouches.isEmpty()) {
                    UIGestureRecognizerStateEnded
                } else {
                    UIGestureRecognizerStateChanged
                }
            }
        } else {
            if (trackedTouches.isEmpty()) {
                // Explicitly fail the gesture, cancelling a scheduled failure
                gestureRecognizer?.cancelFailure()

                gestureRecognizerState = UIGestureRecognizerStateFailed
            }
        }
    }

    /**
     * Implementation of [CMPGestureRecognizerHandlerProtocol] that handles touchesCancelled event and
     * forwards it here.
     *
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized. Just update the gesture
     * recognizer state and pass touches to the Compose runtime.
     *
     * 2. An interop view is hit-tested. In this case if there are no tracked touches left -
     * we need to allow all the touches to be passed to the interop view by failing explicitly.
     */
    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent?) {
        onTouchesEvent(trackedTouches, withEvent, CupertinoTouchesPhase.CANCELLED)
        
        stopTrackingTouches(touches)

        if (hitTestResult == InteractionUIViewHitTestResult.Self) {
            // Golden path, just update the gesture recognizer state.

            if (gestureRecognizerState.isOngoing) {
                gestureRecognizerState = if (trackedTouches.isEmpty()) {
                    UIGestureRecognizerStateCancelled
                } else {
                    UIGestureRecognizerStateChanged
                }
            }
        } else {
            if (trackedTouches.isEmpty()) {
                // Those were the last touches in the sequence
                // Explicitly fail the gesture, cancelling a scheduled failure
                gestureRecognizer?.cancelFailure()

                gestureRecognizerState = UIGestureRecognizerStateFailed
            }
        }
    }

    /**
     * Implementation of [CMPGestureRecognizerHandlerProtocol] that handles the failure of
     * the gesture if it's not recognized within the certain time frame.
     *
     * It means we need to pass all the tracked touches to the runtime as cancelled and set failed
     * state on the gesture recognizer.
     *
     * Intercepted touches will be passed to the interop views by UIKit due to
     * [UIGestureRecognizer.delaysTouchesBegan]
     */
    override fun onFailure() {
        gestureRecognizerState = UIGestureRecognizerStateFailed

        // We won't receive other touches events until all fingers are lifted, so we can't rely
        // on touchesEnded/touchesCancelled to reset the state.  We need to immediately notify
        // the runtime about the cancelled touches and reset the state manually
        onTouchesEvent(trackedTouches, null, CupertinoTouchesPhase.CANCELLED)
        stopTrackingTouches(trackedTouches)
    }

    /**
     * Intentionally clean up all dependencies of GestureRecognizerHandlerImpl to prevent retain cycles that
     * can be caused by implicit capture of the view by UIKit objects (such as UIEvent).
     */
    fun dispose() {
        onTouchesEvent = { _, _, _, _ -> }
        gestureRecognizer = null
        trackedTouches.clear()
    }

    /**
     * Starts tracking the given touches. Remember initial location if those are the first touches
     * in the sequence.
     * @return `true` if the touches are initial, `false` otherwise.
     */
    private fun startTrackingTouches(touches: Set<*>): Boolean {
        val areTouchesInitial = trackedTouches.isEmpty()

        for (touch in touches) {
            trackedTouches.add(touch as UITouch)
        }

        onTouchesCountChanged(touches.size)

        if (areTouchesInitial) {
            initialLocation = trackedTouchesCentroidLocation
        }

        return areTouchesInitial
    }

    /**
     * Check if the tracked touches have moved enough to recognize the gesture.
     */
    private fun checkPanIntent() {
        if (isLocationDeltaAboveSlop) {
            gestureRecognizer?.cancelFailure()
            gestureRecognizerState = UIGestureRecognizerStateBegan
        }
    }

    /**
     * Stops tracking the given touches. If there are no tracked touches left, reset the initial
     * location to null.
     */
    private fun stopTrackingTouches(touches: Set<*>) {
        for (touch in touches) {
            trackedTouches.remove(touch as UITouch)
        }

        onTouchesCountChanged(-touches.size)

        if (trackedTouches.isEmpty()) {
            initialLocation = null
        }
    }

    private fun onTouchesEvent(
        touches: Set<*>,
        event: UIEvent?,
        phase: CupertinoTouchesPhase
    ) {
        val view = view ?: return

        onTouchesEvent(view, touches, event, phase)
    }
}

/**
 * [UIView] subclass that handles touches and keyboard presses events and forwards them
 * to the Compose runtime.
 *
 * @param hitTestInteropView A callback to find an [InteropView] at the given point.
 * @param onTouchesEvent A callback to notify the Compose runtime about touch events.
 * @param onTouchesCountChange A callback to notify the Compose runtime about the number of tracked
 * touches.
 * @param inInteractionBounds A callback to check if the given point is within the interaction
 * bounds as defined by the owning implementation.
 * @param onKeyboardPresses A callback to notify the Compose runtime about keyboard presses.
 * The parameter is a [Set] of [UIPress] objects. Erasure happens due to K/N not supporting Obj-C
 * lightweight generics.
 */
internal class InteractionUIView(
    private var hitTestInteropView: (point: CValue<CGPoint>, event: UIEvent?) -> UIView?,
    onTouchesEvent: (view: UIView, touches: Set<*>, event: UIEvent?, phase: CupertinoTouchesPhase) -> Unit,
    private var onTouchesCountChange: (count: Int) -> Unit,
    private var inInteractionBounds: (CValue<CGPoint>) -> Boolean,
    private var onKeyboardPresses: (Set<*>) -> Unit,
) : UIView(CGRectZero.readValue()) {
    private val gestureRecognizerHandler = GestureRecognizerHandlerImpl(
        view = this,
        onTouchesEvent = onTouchesEvent,
        onTouchesCountChanged = { touchesCount += it }
    )

    private val gestureRecognizer = CMPGestureRecognizer()

    /**
     * When there at least one tracked touch, we need notify redrawer about it. It should schedule
     * CADisplayLink which affects frequency of polling UITouch events on high frequency display
     * and forces it to match display refresh rate.
     */
    private var touchesCount = 0
        set(value) {
            field = value
            onTouchesCountChange(value)
        }

    init {
        multipleTouchEnabled = true
        userInteractionEnabled = true

        // When CMPGestureRecognizer is recognized, immediately cancel all touches in the subviews.
        gestureRecognizer.cancelsTouchesInView = true

        // Delays touches reception by underlying views until the gesture recognizer is explicitly
        // stated as failed (aka, the touch sequence is targeted to the interop view).
        gestureRecognizer.delaysTouchesBegan = true

        addGestureRecognizer(gestureRecognizer)
        gestureRecognizer.handler = gestureRecognizerHandler
        gestureRecognizerHandler.gestureRecognizer = gestureRecognizer
    }

    override fun canBecomeFirstResponder() = true

    override fun pressesBegan(presses: Set<*>, withEvent: UIPressesEvent?) {
        onKeyboardPresses(presses)
        super.pressesBegan(presses, withEvent)
    }

    override fun pressesEnded(presses: Set<*>, withEvent: UIPressesEvent?) {
        onKeyboardPresses(presses)
        super.pressesEnded(presses, withEvent)
    }

    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? =
        savingHitTestResult {
            if (!inInteractionBounds(point)) {
                null
            } else {
                // Find if a scene contains an [InteropView]
                val interopView = hitTestInteropView(point, withEvent)

                if (interopView == null) {
                    // Native [hitTest] happens after [pointInside] is checked. If hit testing
                    // inside ComposeScene didn't yield any interop view, then we should return [this]
                    this
                } else {
                    // Transform the point to the interop view's coordinate system.
                    // And perform native [hitTest] on the interop view.
                    // Return this view if the interop view doesn't handle the hit test.
                    interopView.hitTest(
                        point = convertPoint(point, toView = interopView),
                        withEvent = withEvent
                    ) ?: this
                }
            }
        }

    /**
     * Intentionally clean up all dependencies of InteractionUIView to prevent retain cycles that
     * can be caused by implicit capture of the view by UIKit objects (such as UIEvent).
     */
    fun dispose() {
        gestureRecognizerHandler.dispose()
        gestureRecognizer.handler = null
        removeGestureRecognizer(gestureRecognizer)

        hitTestInteropView = { _, _ -> null }

        onTouchesCountChange = {}
        inInteractionBounds = { false }
        onKeyboardPresses = {}
    }

    /**
     * Execute the given [hitTestBlock] and save the result to the gesture recognizer handler, so
     * that it can be used later to determine if the gesture recognizer should be recognized
     * or failed.
     */
    private fun savingHitTestResult(hitTestBlock: () -> UIView?): UIView? {
        val result = hitTestBlock()
        gestureRecognizerHandler.hitTestResult = if (result == null) {
            null
        } else {
            if (result == this) {
                InteractionUIViewHitTestResult.Self
            } else {
                // All views beneath are considered to be interop views.
                // If the hit-tested view is not a descendant of [InteropWrappingView], then it
                // should be considered as a view that doesn't want to cooperate with Compose.

                val interactionMode = result.findAncestorInteropWrappingView()?.interactionMode

                when (interactionMode) {
                    is UIKitInteropInteractionMode.Cooperative -> {
                        InteractionUIViewHitTestResult.CooperativeChildView(
                            delay = interactionMode.delay
                        )
                    }

                    is UIKitInteropInteractionMode.NonCooperative -> {
                        InteractionUIViewHitTestResult.NonCooperativeChildView
                    }

                    null -> InteractionUIViewHitTestResult.Self
                }
            }
        }
        return result
    }
}

/**
 * There is no way to associate [InteropWrappingView.interactionMode] with a given hitTest query.
 * This extension property allows to find the nearest [InteropWrappingView] up the view hierarchy
 * and request the value retroactively.
 */
private fun UIView.findAncestorInteropWrappingView(): InteropWrappingView? {
    var view: UIView? = this
    while (view != null) {
        if (view is InteropWrappingView) {
            return view
        }
        view = view.superview
    }
    return null
}

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

package androidx.compose.material3.adaptive.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PaneScaffoldDirectiveTest {
    @Test
    fun test_calculateStandardPaneScaffoldDirective_compactWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(WindowSizeClass.compute(400f, 800f), Posture())
            )

        assertEquals(1, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(0.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_mediumWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(WindowSizeClass.compute(750f, 900f), Posture())
            )

        assertEquals(1, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(0.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_expandedWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(WindowSizeClass.compute(1200f, 800f), Posture())
            )

        assertEquals(2, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(24.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_tabletop() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(WindowSizeClass.compute(700f, 800f), Posture(isTabletop = true))
            )

        assertEquals(1, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(2, scaffoldDirective.maxVerticalPartitions)
        assertEquals(0.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(24.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_compactWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(WindowSizeClass.compute(400f, 800f), Posture())
            )

        assertEquals(1, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(0.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_mediumWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(WindowSizeClass.compute(750f, 900f), Posture())
            )

        assertEquals(2, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(24.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_expandedWidth() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(WindowSizeClass.compute(1200f, 800f), Posture())
            )

        assertEquals(2, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(1, scaffoldDirective.maxVerticalPartitions)
        assertEquals(24.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(0.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_tabletop() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(WindowSizeClass.compute(700f, 800f), Posture(isTabletop = true))
            )

        assertEquals(2, scaffoldDirective.maxHorizontalPartitions)
        assertEquals(2, scaffoldDirective.maxVerticalPartitions)
        assertEquals(24.dp, scaffoldDirective.horizontalPartitionSpacerSize)
        assertEquals(24.dp, scaffoldDirective.verticalPartitionSpacerSize)
        assertEquals(360.dp, scaffoldDirective.defaultPanePreferredWidth)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_alwaysAvoidHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AlwaysAvoid
            )

        assertEquals(hingeList.getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_avoidOccludingHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AvoidOccluding
            )

        assertEquals(hingeList.subList(0, 2).getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_avoidSeparatingHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AvoidSeparating
            )

        assertEquals(hingeList.subList(2, 3).getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateStandardPaneScaffoldDirective_neverAvoidHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirective(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.NeverAvoid
            )

        assertTrue { scaffoldDirective.excludedBounds.isEmpty() }
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_alwaysAvoidHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AlwaysAvoid
            )

        assertEquals(hingeList.getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_avoidOccludingHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AvoidOccluding
            )

        assertEquals(hingeList.subList(0, 2).getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_avoidSeparatingHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.AvoidSeparating
            )

        assertEquals(hingeList.subList(2, 3).getBounds(), scaffoldDirective.excludedBounds)
    }

    @Test
    fun test_calculateDensePaneScaffoldDirective_neverAvoidHinge() {
        val scaffoldDirective =
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
                WindowAdaptiveInfo(
                    WindowSizeClass.compute(700f, 800f),
                    Posture(hingeList = hingeList)
                ),
                HingePolicy.NeverAvoid
            )

        assertTrue { scaffoldDirective.excludedBounds.isEmpty() }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val hingeList =
    listOf(
        HingeInfo(
            bounds = Rect(0F, 0F, 1F, 1F),
            isFlat = true,
            isVertical = true,
            isSeparating = false,
            isOccluding = true
        ),
        HingeInfo(
            bounds = Rect(1F, 1F, 2F, 2F),
            isFlat = false,
            isVertical = true,
            isSeparating = false,
            isOccluding = true
        ),
        HingeInfo(
            bounds = Rect(2F, 2F, 3F, 3F),
            isFlat = true,
            isVertical = true,
            isSeparating = true,
            isOccluding = false
        ),
    )

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun List<HingeInfo>.getBounds(): List<Rect> {
    return map { it.bounds }
}

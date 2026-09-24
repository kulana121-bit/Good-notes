package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion

private data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val badgeIcon: ImageVector,
    val accentColor: Color,
    val pills: List<String>
)

private val pages = listOf(
    OnboardingPageData(
        title = "Notes stay available offline",
        subtitle = "Write notes, create task checklists, and record voice memos. Everything is stored locally on this device with zero latency.",
        badgeIcon = Icons.Outlined.EditNote,
        accentColor = Color(0xFFEB7A53),
        pills = listOf("Instant offline access", "Fast local SQLite", "Zero cloud dependency")
    ),
    OnboardingPageData(
        title = "Google Drive Backup",
        subtitle = "Your data stays yours. Safely back up notes, folders, and PDF documents directly into your personal Google Drive storage.",
        badgeIcon = Icons.Outlined.Description,
        accentColor = Color(0xFFE5B422),
        pills = listOf("User-owned storage", "PDF document preservation", "Versioned snapshots")
    ),
    OnboardingPageData(
        title = "Seamless Cloud Restore",
        subtitle = "Sign in with Google to sync notes across devices or effortlessly restore your complete workspace on any new phone.",
        badgeIcon = Icons.Outlined.CloudDone,
        accentColor = Color(0xFF8BBF50),
        pills = listOf("Google Identity", "One-tap full restore", "Automatic sync")
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    var currentPage by remember { mutableIntStateOf(0) }

    val pageData = pages[currentPage]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage < pages.size - 1) {
                    TextButton(
                        onClick = {
                            haptics.performTap()
                            onComplete()
                        },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = "Skip",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // Center Content with horizontal animated transition
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> if (isReducedMotion) 0 else width / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> if (isReducedMotion) 0 else -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> if (isReducedMotion) 0 else -width / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> if (isReducedMotion) 0 else width / 3 } + fadeOut())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "onboarding_page_content"
            ) { targetIndex ->
                val page = pages[targetIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Tactile Hero Icon Card
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .shadow(12.dp, RoundedCornerShape(32.dp), ambientColor = page.accentColor.copy(alpha = 0.35f))
                            .clip(RoundedCornerShape(32.dp))
                            .background(page.accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.badgeIcon,
                            contentDescription = null,
                            tint = Color(0xFF1E1E1E),
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = OutfitFontFamily,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Tactile feature capsules
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        page.pills.forEach { pillText ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = page.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = pillText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation: Indicator Dots + Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Page Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.indices.forEach { index ->
                        val isSelected = index == currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = MotionTokens.standardTween(),
                            label = "dot_width"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isSelected) pageData.accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            animationSpec = MotionTokens.standardTween(),
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                                .clickable {
                                    haptics.performTap()
                                    currentPage = index
                                }
                        )
                    }
                }

                // Primary Next / Get Started Button
                val isLastPage = currentPage == pages.size - 1
                val buttonInteraction = remember { MutableInteractionSource() }
                val isPressed by buttonInteraction.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed && !isReducedMotion) 0.97f else 1.0f,
                    animationSpec = MotionTokens.subtlePressSpring(),
                    label = "btn_scale"
                )

                Button(
                    onClick = {
                        haptics.performTap()
                        if (isLastPage) {
                            onComplete()
                        } else {
                            currentPage++
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(buttonScale)
                        .testTag("onboarding_action_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pageData.accentColor,
                        contentColor = Color(0xFF1E1E1E)
                    ),
                    interactionSource = buttonInteraction
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLastPage) "Get Started" else "Next",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

package to.ottomot.driftd.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import to.ottomot.driftd.ui.insets.OttoWindowInsets.ottoBottomSheetContent
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import to.ottomot.driftd.R

private enum class MarketingSlideKind {
    Welcome,
    Feature,
}

private data class MarketingSlideBullet(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private data class MarketingSlide(
    val kind: MarketingSlideKind,
    @DrawableRes val imageRes: Int,
    val headlinePlain: String,
    val headlineGradient: String,
    val body: String,
    val bullets: List<MarketingSlideBullet>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarketingOnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedSlides = marketingOnboardingSlidesResolved()

    val pagerState =
        rememberPagerState(
            initialPage = 0,
            pageCount = { resolvedSlides.size },
        )
    val scope = rememberCoroutineScope()
    val parallax by animateFloatAsState(
        targetValue = pagerState.currentPage.toFloat(),
        animationSpec = tween(durationMillis = 420),
        label = "onboardingParallax",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { resolvedSlides[it].imageRes },
        ) { page ->
            val slide = resolvedSlides[page]
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(slide.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .offset(y = ((parallax - page) * 8f).dp),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (slide.kind == MarketingSlideKind.Welcome) {
                                Modifier
                            } else {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.12f),
                                        0.5f to Color.Black.copy(alpha = 0.5f),
                                        1f to Color.Black.copy(alpha = 0.92f),
                                    ),
                                )
                            },
                        ),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (slide.kind == MarketingSlideKind.Welcome) {
                                    Modifier
                                } else {
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = 138.dp)
                                },
                            ),
                    verticalArrangement =
                        if (slide.kind == MarketingSlideKind.Welcome) {
                            Arrangement.Top
                        } else {
                            Arrangement.Bottom
                        },
                    horizontalAlignment =
                        if (slide.kind == MarketingSlideKind.Welcome) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        },
                ) {
                    if (slide.kind == MarketingSlideKind.Feature) {
                        MarketingHeadline(plain = slide.headlinePlain, gradient = slide.headlineGradient, center = false)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = slide.body,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (slide.bullets.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            slide.bullets.forEach { bullet ->
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                bullet.icon,
                                                contentDescription = null,
                                                tint = Color(0xFFDAA6FF),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(
                                            bullet.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                        )
                                        Text(
                                            bullet.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.62f),
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .ottoBottomSheetContent()
                .padding(horizontal = 22.dp)
                .padding(bottom = 6.dp),
        ) {
            val lastPage = pagerState.currentPage >= resolvedSlides.lastIndex
            if (pagerState.currentPage == 0) {
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    contentPadding = PaddingValues(),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(ottoAccentBrush(), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(
                                stringResource(R.string.marketing_onboarding_continue),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                " ›",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onFinished) {
                        Text(
                            stringResource(R.string.marketing_onboarding_skip),
                            color = Color.White.copy(alpha = 0.62f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (lastPage) {
                                onFinished()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    ) {
                        Text(
                            if (lastPage) {
                                stringResource(R.string.marketing_onboarding_get_started)
                            } else {
                                stringResource(R.string.marketing_onboarding_next)
                            } + " ›",
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(brush = ottoAccentBrush()),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(resolvedSlides.size) { i ->
                    val sel = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (sel) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (sel) {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF9440FA),
                                            Color(0xFFF8629D),
                                        ),
                                    )
                                } else {
                                    SolidColor(Color.White.copy(alpha = 0.22f))
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun marketingOnboardingSlidesResolved(): List<MarketingSlide> =
    listOf(
        MarketingSlide(
            kind = MarketingSlideKind.Welcome,
            imageRes = R.drawable.onboarding_slide_0,
            headlinePlain = stringResource(R.string.marketing_onboarding_welcome_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_welcome_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_welcome_body),
            bullets = emptyList(),
        ),
        MarketingSlide(
            kind = MarketingSlideKind.Feature,
            imageRes = R.drawable.onboarding_slide_1,
            headlinePlain = stringResource(R.string.marketing_onboarding_squads_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_squads_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_squads_body),
            bullets =
                listOf(
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_squads_b1t),
                        stringResource(R.string.marketing_onboarding_squads_b1s),
                        Icons.Outlined.Chat,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_squads_b2t),
                        stringResource(R.string.marketing_onboarding_squads_b2s),
                        Icons.Outlined.Lock,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_squads_b3t),
                        stringResource(R.string.marketing_onboarding_squads_b3s),
                        Icons.Outlined.Groups,
                    ),
                ),
        ),
        MarketingSlide(
            kind = MarketingSlideKind.Feature,
            imageRes = R.drawable.onboarding_slide_2,
            headlinePlain = stringResource(R.string.marketing_onboarding_map_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_map_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_map_body),
            bullets =
                listOf(
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_map_b1t),
                        stringResource(R.string.marketing_onboarding_map_b1s),
                        Icons.Outlined.MyLocation,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_map_b2t),
                        stringResource(R.string.marketing_onboarding_map_b2s),
                        Icons.Outlined.Bookmark,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_map_b3t),
                        stringResource(R.string.marketing_onboarding_map_b3s),
                        Icons.Outlined.Refresh,
                    ),
                ),
        ),
        MarketingSlide(
            kind = MarketingSlideKind.Feature,
            imageRes = R.drawable.onboarding_slide_4,
            headlinePlain = stringResource(R.string.marketing_onboarding_garage_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_garage_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_garage_body),
            bullets =
                listOf(
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_garage_b1t),
                        stringResource(R.string.marketing_onboarding_garage_b1s),
                        Icons.Outlined.AddCircleOutline,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_garage_b2t),
                        stringResource(R.string.marketing_onboarding_garage_b2s),
                        Icons.Outlined.Star,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_garage_b3t),
                        stringResource(R.string.marketing_onboarding_garage_b3s),
                        Icons.Outlined.People,
                    ),
                ),
        ),
        MarketingSlide(
            kind = MarketingSlideKind.Feature,
            imageRes = R.drawable.onboarding_slide_3,
            headlinePlain = stringResource(R.string.marketing_onboarding_events_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_events_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_events_body),
            bullets =
                listOf(
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_events_b1t),
                        stringResource(R.string.marketing_onboarding_events_b1s),
                        Icons.Outlined.Event,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_events_b2t),
                        stringResource(R.string.marketing_onboarding_events_b2s),
                        Icons.Outlined.ThumbUp,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_events_b3t),
                        stringResource(R.string.marketing_onboarding_events_b3s),
                        Icons.Outlined.NearMe,
                    ),
                ),
        ),
        MarketingSlide(
            kind = MarketingSlideKind.Feature,
            imageRes = R.drawable.onboarding_slide_5,
            headlinePlain = stringResource(R.string.marketing_onboarding_progress_headline_plain),
            headlineGradient = stringResource(R.string.marketing_onboarding_progress_headline_gradient),
            body = stringResource(R.string.marketing_onboarding_progress_body),
            bullets =
                listOf(
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_progress_b1t),
                        stringResource(R.string.marketing_onboarding_progress_b1s),
                        Icons.Outlined.TrendingUp,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_progress_b2t),
                        stringResource(R.string.marketing_onboarding_progress_b2s),
                        Icons.Outlined.MilitaryTech,
                    ),
                    MarketingSlideBullet(
                        stringResource(R.string.marketing_onboarding_progress_b3t),
                        stringResource(R.string.marketing_onboarding_progress_b3s),
                        Icons.Outlined.CalendarMonth,
                    ),
                ),
        ),
    )

@Composable
private fun MarketingHeadline(
    plain: String,
    gradient: String,
    center: Boolean,
) {
    val brush = ottoAccentBrush()
    val plainTrimmed = plain.trimEnd()
    val gradientTrimmed = gradient.trimStart()
    val annotated: AnnotatedString =
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append(plainTrimmed)
                if (plainTrimmed.isNotEmpty() && gradientTrimmed.isNotEmpty()) {
                    append(' ')
                }
            }
            if (gradientTrimmed.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        brush = brush,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(gradientTrimmed)
                }
            }
        }
    Text(
        text = annotated,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

private fun ottoAccentBrush(): Brush =
    Brush.linearGradient(
        colors = listOf(Color(0xFF9440FA), Color(0xFFF8629D)),
    )

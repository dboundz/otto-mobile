package to.ottomot.driftd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import to.ottomot.driftd.core.network.dto.ProfileLevelUpDto
import to.ottomot.driftd.core.network.dto.ProfileProgressionDto

@Composable
internal fun ProfileLevelUpModal(
    levelUp: ProfileLevelUpDto,
    onContinue: () -> Unit,
) {
    Dialog(
        onDismissRequest = onContinue,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
    ) {
        ProfileLevelUpModalContent(
            levelUp = levelUp,
            onContinue = onContinue,
            modifier = Modifier.zIndex(150f),
        )
    }
}

@Composable
private fun ProfileLevelUpModalContent(
    levelUp: ProfileLevelUpDto,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progression = levelUp.progression ?: return
    val tierColor = profileTierComposeColor(progression.tierId)
    val cardShape = RoundedCornerShape(28.dp)
    val continueGradient =
        Brush.horizontalGradient(
            colors =
                listOf(
                    Color(0xFF3326FF),
                    Color(0xFFB833E0),
                ),
        )

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = cardShape,
                        ambientColor = tierColor.copy(alpha = 0.35f),
                        spotColor = tierColor.copy(alpha = 0.35f),
                    ),
            shape = cardShape,
            color = Color.Black.copy(alpha = 0.92f),
            border = BorderStroke(1.5.dp, tierColor.copy(alpha = 0.56f)),
        ) {
            Column(
                modifier =
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        tierColor.copy(alpha = 0.22f),
                                        Color.Transparent,
                                    ),
                            ),
                        )
                        .padding(horizontal = 40.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_level_up_title),
                        color = tierColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.profile_level_up_reached_prefix),
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = levelUp.reachedDisplayName.orEmpty(),
                            color = tierColor,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Image(
                    painter = painterResource(progressionLevelBadgeRes(progression.level)),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(230.dp)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = tierColor.copy(alpha = 0.75f),
                                spotColor = tierColor.copy(alpha = 0.75f),
                            ),
                )

                levelUp.nextProgression?.let { next ->
                    ProfileLevelUpNextRow(
                        levelUp = levelUp,
                        nextProgression = next,
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(continueGradient, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(vertical = 17.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_level_up_continue),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileLevelUpNextRow(
    levelUp: ProfileLevelUpDto,
    nextProgression: ProfileProgressionDto,
) {
    val nextColor = profileTierComposeColor(nextProgression.tierId)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(progressionLevelBadgeRes(nextProgression.level)),
            contentDescription = null,
            modifier =
                Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = nextColor.copy(alpha = 0.45f),
                        spotColor = nextColor.copy(alpha = 0.45f),
                    ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_level_up_next_up),
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = levelUp.nextDisplayName ?: nextProgression.tierName.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            nextProgression.level?.let { level ->
                Text(
                    text = stringResource(R.string.profile_level_up_next_level_format, level),
                    color = Color.White.copy(alpha = 0.62f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )
    }
}

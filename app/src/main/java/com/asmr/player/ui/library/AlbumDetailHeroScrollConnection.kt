package com.asmr.player.ui.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroBounceBackSpec
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingApproachMillis
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingOvershootMaxPortion
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingOvershootPortion
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingSettleMillis
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingVelocityMax
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroFlingVelocityMin
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroMotionState
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroOvershootReleaseMultiplier
import com.asmr.player.ui.library.albumdetail.AlbumDetailHeroOvershootResistance
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 详情页 hero 折叠/回弹 NestedScrollConnection。自 AlbumDetailScreen 抽出，行为逐字保持。 */
@Composable
internal fun rememberAlbumDetailHeroNestedScrollConnection(
    heroMotion: AlbumDetailHeroMotionState,
    scope: CoroutineScope,
    heroCollapseMaxPx: Float,
    heroVisualOvershootMaxPx: Float
): NestedScrollConnection {
    return remember(
        heroCollapseMaxPx,
        heroVisualOvershootMaxPx,
        heroMotion,
        scope
    ) {
        object : NestedScrollConnection {
            private fun settleVisualOvershoot(initialVelocity: Float = 0f): Boolean {
                val start = heroMotion.visualOvershootPx
                if (abs(start) < 0.5f) return false
                heroMotion.cancelVisualOvershootAnimation()
                heroMotion.visualOvershootJob = scope.launch {
                    animate(
                        initialValue = start,
                        targetValue = 0f,
                        initialVelocity = initialVelocity,
                        animationSpec = AlbumDetailHeroBounceBackSpec
                    ) { value, _ ->
                        heroMotion.visualOvershootPx = value
                    }
                }
                return true
            }

            private fun dragOvershootDelta(delta: Float): Float {
                val progress = (-heroMotion.visualOvershootPx / heroVisualOvershootMaxPx)
                    .coerceIn(0f, 1f)
                val resistance = AlbumDetailHeroOvershootResistance * (1f - progress * progress * 0.62f)
                return delta * resistance
            }

            private fun applyCollapseDelta(delta: Float): Float {
                if (delta == 0f) return 0f
                heroMotion.cancelVisualOvershootAnimation()
                val current = heroMotion.collapsePx.coerceIn(0f, heroCollapseMaxPx)
                var remaining = delta
                var consumed = 0f

                if (remaining > 0f && heroMotion.visualOvershootPx < 0f) {
                    val visualRelease = (remaining * AlbumDetailHeroOvershootReleaseMultiplier)
                        .coerceAtMost(-heroMotion.visualOvershootPx)
                    if (visualRelease > 0f) {
                        heroMotion.visualOvershootPx += visualRelease
                        remaining -= visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                        consumed += visualRelease / AlbumDetailHeroOvershootReleaseMultiplier
                    }
                }

                if (remaining != 0f) {
                    val collapseTarget = (current + remaining).coerceIn(0f, heroCollapseMaxPx)
                    val collapseApplied = collapseTarget - current
                    if (collapseApplied != 0f) {
                        heroMotion.collapsePx = collapseTarget
                        remaining -= collapseApplied
                        consumed += collapseApplied
                    }
                }

                if (remaining < 0f && heroVisualOvershootMaxPx > 0f) {
                    val visualDelta = dragOvershootDelta(remaining)
                    val visualTarget = (heroMotion.visualOvershootPx + visualDelta)
                        .coerceIn(-heroVisualOvershootMaxPx, 0f)
                    heroMotion.visualOvershootPx = visualTarget
                    consumed += remaining
                }

                return consumed
            }

            private fun flingOvershootTarget(velocityY: Float): Float {
                if (velocityY <= AlbumDetailHeroFlingVelocityMin) return 0f
                val velocityProgress = ((velocityY - AlbumDetailHeroFlingVelocityMin) /
                    (AlbumDetailHeroFlingVelocityMax - AlbumDetailHeroFlingVelocityMin))
                    .coerceIn(0f, 1f)
                val eased = velocityProgress * velocityProgress
                val target = heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootPortion * eased
                val cappedTarget = target.coerceAtMost(
                    heroVisualOvershootMaxPx * AlbumDetailHeroFlingOvershootMaxPortion
                )
                return -cappedTarget
            }

            private fun absorbFlingOvershoot(velocityY: Float): Boolean {
                val target = flingOvershootTarget(velocityY)
                if (target >= -0.5f) return settleVisualOvershoot()
                heroMotion.cancelVisualOvershootAnimation()
                heroMotion.visualOvershootJob = scope.launch {
                    if (target < heroMotion.visualOvershootPx) {
                        animate(
                            initialValue = heroMotion.visualOvershootPx,
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = AlbumDetailHeroFlingApproachMillis,
                                easing = FastOutSlowInEasing
                            )
                        ) { value, _ -> heroMotion.visualOvershootPx = value }
                    }
                    animate(
                        initialValue = heroMotion.visualOvershootPx,
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = AlbumDetailHeroFlingSettleMillis,
                            easing = FastOutSlowInEasing
                        )
                    ) { value, _ -> heroMotion.visualOvershootPx = value }
                }
                return true
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                // 向上浏览（手指上滑，dy<0）：先把滚动用于折叠 hero，再交给列表。
                if (dy < 0f && (
                        heroMotion.collapsePx < heroCollapseMaxPx ||
                            heroMotion.visualOvershootPx < 0f
                        )
                ) {
                    val applied = applyCollapseDelta(-dy)
                    val consumed = if (applied != 0f) -applied else dy
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = available.y
                // 列表已到顶仍有下滑剩余（dy>0）：把剩余滚动用于展开 hero。
                if (dy > 0f && (
                        heroMotion.collapsePx > 0f ||
                            heroMotion.visualOvershootPx > -heroVisualOvershootMaxPx
                        )
                ) {
                    val applied = applyCollapseDelta(-dy)
                    val released = if (applied != 0f) -applied else dy
                    return Offset(0f, released)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settleVisualOvershoot()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f && heroMotion.collapsePx <= 0.5f) {
                    absorbFlingOvershoot(available.y)
                } else {
                    settleVisualOvershoot()
                }
                return Velocity.Zero
            }
        }
    }
}

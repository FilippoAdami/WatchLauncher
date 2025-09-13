package com.example.watchlauncher

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

class CarouselLayoutManager(context: Context) : LinearLayoutManager(context, RecyclerView.VERTICAL, false) {

    private val shrinkAmount = 0.5f
    private val shrinkDistance = 0.9f

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        super.onLayoutChildren(recycler, state)
        scaleAndAlphaViews()
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        if (scrolled != 0) {
            scaleAndAlphaViews()
        }
        return scrolled
    }

    private fun scaleAndAlphaViews() {
        val midpoint = height / 2.0f
        val d0 = 0f
        val d1 = shrinkDistance * midpoint
        val s0 = 1f
        val s1 = 1f - shrinkAmount
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childMidpoint = (getDecoratedTop(child) + getDecoratedBottom(child)) / 2.0f
            val distance = abs(midpoint - childMidpoint)
            val scale = if (distance < d1) {
                s0 + (s1 - s0) * (distance / d1)
            } else {
                s1
            }
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = if (distance < d1) {
                1.0f - (distance / d1).pow(2) * 0.5f
            } else {
                0.5f
            }
        }
    }

    /**
     * Finds the adapter position of the item in the center of the RecyclerView.
     */
    fun getCenterItemPosition(): Int {
        if (childCount == 0) return RecyclerView.NO_POSITION

        val center = height / 2.0f
        var minDistance = Float.MAX_VALUE
        var centerChildPosition = -1

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childCenter = (getDecoratedTop(child) + getDecoratedBottom(child)) / 2.0f
            val distance = abs(center - childCenter)

            if (distance < minDistance) {
                minDistance = distance
                centerChildPosition = getPosition(child)
            }
        }
        return centerChildPosition
    }
}

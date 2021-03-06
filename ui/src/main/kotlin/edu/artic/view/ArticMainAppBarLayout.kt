package edu.artic.view

import android.content.Context
import android.support.annotation.DrawableRes
import android.support.design.widget.AppBarLayout
import android.util.AttributeSet
import android.view.View
import edu.artic.ui.R
import kotlinx.android.synthetic.main.view_app_bar_layout.view.*

/**
 * Description: Wraps the layout and common functionality for the main collapsing [AppBarLayout] in
 * this app.
 */
class ArticMainAppBarLayout(context: Context, attrs: AttributeSet? = null) : AppBarLayout(context, attrs) {

    init {
        View.inflate(context, R.layout.view_app_bar_layout, this)
        fitsSystemWindows = true

        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                    attrs,
                    R.styleable.ArticMainAppBarLayout,
                    0, 0
            )
            setIcon(a.getResourceId(R.styleable.ArticMainAppBarLayout_icon, 0))
            setBackgroundImage(a.getResourceId(R.styleable.ArticMainAppBarLayout_backgroundImage, 0))
        }

        // update our content when offset changes.
        addOnOffsetChangedListener { aBarLayout, verticalOffset ->
            val progress: Double = 1 - Math.abs(verticalOffset) / aBarLayout.totalScrollRange.toDouble()
            searchIcon.background.alpha = (progress * 255).toInt()
            icon.drawable.alpha = (progress * 255).toInt()
            expandedImage.drawable.alpha = (progress * 255).toInt()
        }
    }

    fun setIcon(@DrawableRes iconId: Int) {
        icon.setImageResource(iconId)
    }

    fun setBackgroundImage(@DrawableRes imageId: Int) {
        expandedImage.setImageResource(imageId)
    }

}
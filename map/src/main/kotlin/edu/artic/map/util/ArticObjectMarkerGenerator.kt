package edu.artic.map.util

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import edu.artic.map.R


class ArticObjectMarkerGenerator(context: Context) : BaseMarkerGenerator(context) {

    /**
     * Where the [icon][BaseMarkerGenerator.makeIcon] will appear.
     */
    private val target: ImageView

    init {
        container = LayoutInflater.from(context)
                .inflate(R.layout.marker_artic_object, null) as ViewGroup
        target = container.findViewById(R.id.circularImage)
    }


    /**
     * NB: 'imageViewBitmap' _MUST_ be rendered in software. If its config
     * is [android.graphics.Bitmap.Config.HARDWARE], the
     * [CircleImageView][de.hdodenhof.circleimageview.CircleImageView]
     * may crash.
     */
    fun makeIcon(imageViewBitmap: Bitmap): Bitmap {
        target.setImageBitmap(imageViewBitmap)
        return makeIcon()
    }
}
package edu.artic.tours

import android.view.View
import com.bumptech.glide.Glide
import com.fuzz.rx.bindToMain
import com.fuzz.rx.disposedBy
import com.jakewharton.rxbinding2.widget.text
import edu.artic.adapter.AutoHolderRecyclerViewAdapter
import edu.artic.adapter.BaseViewHolder
import kotlinx.android.synthetic.main.cell_tour_details_stop.view.*

class TourDetailsStopAdapter : AutoHolderRecyclerViewAdapter<TourDetailsStopCellViewModel>() {

    override fun View.onBindView(item: TourDetailsStopCellViewModel, position: Int) {
        item.titleText
                .bindToMain(tourStopTitle.text())
                .disposedBy(item.viewDisposeBag)
        item.galleryText
                .bindToMain(tourStopGallery.text())
                .disposedBy(item.viewDisposeBag)
        item.stopNumber
                .bindToMain(tourNumber.text())
                .disposedBy(item.viewDisposeBag)
        item.imageUrl
                .subscribe {
                    Glide.with(this)
                            .load(it)
                            .into(image)
                }
                .disposedBy(item.viewDisposeBag)
    }

    override fun getLayoutResId(position: Int) = R.layout.cell_tour_details_stop

    override fun onItemViewDetachedFromWindow(holder: BaseViewHolder, position: Int) {
        super.onItemViewDetachedFromWindow(holder, position)
        getItem(position).apply {
            cleanup()
            onCleared()
        }
    }
}
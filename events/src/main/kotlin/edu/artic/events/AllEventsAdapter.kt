package edu.artic.events

import android.view.View
import com.bumptech.glide.Glide
import com.fuzz.rx.bindToMain
import com.fuzz.rx.disposedBy
import com.jakewharton.rxbinding2.widget.text
import edu.artic.adapter.AutoHolderRecyclerViewAdapter
import edu.artic.adapter.BaseViewHolder
import kotlinx.android.synthetic.main.cell_all_events_layout.view.*

/**
 * @author Sameer Dhakal (Fuzz)
 */

class AllEventsAdapter : AutoHolderRecyclerViewAdapter<AllEventsCellBaseViewModel>() {

    override fun View.onBindView(item: AllEventsCellBaseViewModel, position: Int) {

        if (item is AllEventsCellViewModel) {

            image.visibility = View.VISIBLE
            title.visibility = View.VISIBLE
            description.visibility = View.VISIBLE
            dateTime.visibility = View.VISIBLE
            spacerLine.visibility = View.VISIBLE
            headerText.visibility = View.GONE

            item.eventImageUrl.subscribe {
                Glide.with(context)
                        .load(it)
                        .into(image)
            }.disposedBy(item.viewDisposeBag)

            item.eventTitle
                    .bindToMain(title.text())
                    .disposedBy(item.viewDisposeBag)

            item.eventDescription
                    .bindToMain(description.text())
                    .disposedBy(item.viewDisposeBag)

            item.eventDateTime
                    .bindToMain(dateTime.text())
                    .disposedBy(item.viewDisposeBag)
        } else if (item is AllEventsCellHeaderViewModel) {
            image.visibility = View.GONE
            title.visibility = View.GONE
            description.visibility = View.GONE
            dateTime.visibility = View.GONE
            spacerLine.visibility = View.GONE
            headerText.visibility = View.VISIBLE

            item.text
                    .bindToMain(headerText.text())
                    .disposedBy(item.viewDisposeBag)
        }


    }

    override fun onItemViewDetachedFromWindow(holder: BaseViewHolder, position: Int) {
        super.onItemViewDetachedFromWindow(holder, position)
        getItem(position).apply {
            cleanup()
            onCleared()
        }
    }

    override fun getLayoutResId(position: Int): Int {
        return R.layout.cell_all_events_layout
    }

}
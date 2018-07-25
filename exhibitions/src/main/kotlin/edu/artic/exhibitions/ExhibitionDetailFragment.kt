package edu.artic.exhibitions

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.transition.TransitionInflater
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.fuzz.rx.bindToMain
import com.fuzz.rx.disposedBy
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.text
import edu.artic.db.models.ArticExhibition
import edu.artic.viewmodel.BaseViewModelFragment
import edu.artic.viewmodel.Navigate
import kotlinx.android.synthetic.main.fragment_exhibition_details.*
import kotlinx.android.synthetic.main.fragment_exhibition_details.view.*
import timber.log.Timber
import kotlin.reflect.KClass


class ExhibitionDetailFragment : BaseViewModelFragment<ExhibitionDetailViewModel>() {

    override val viewModelClass: KClass<ExhibitionDetailViewModel>
        get() = ExhibitionDetailViewModel::class
    override val layoutResId: Int
        get() = R.layout.fragment_exhibition_details
    override val title: String
        get() = ""

    private val exhibition by lazy { arguments!!.getParcelable<ArticExhibition>(ARG_EXHIBITION) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
        sharedElementEnterTransition =
                TransitionInflater.from(context).inflateTransition(android.R.transition.move)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val progress: Double = 1 - Math.abs(verticalOffset) / appBarLayout.totalScrollRange.toDouble()
            if (progress <= .5) {
                val diff = .5f - progress.toFloat()
                if (diff <= .25f) {
                    appBarLayout.toolbarTitle.alpha = 0f
                    appBarLayout.expandedTitle.alpha = 1 - (diff / .25f)
                } else {
                    appBarLayout.expandedTitle.alpha = 0f
                    appBarLayout.toolbarTitle.alpha = (diff / .25f) - 1f
                }
            } else {
                appBarLayout.expandedTitle.alpha = 0f
                appBarLayout.expandedTitle.alpha = 1f
            }

        }
        exhibitionImage.transitionName = exhibition.title
    }

    override fun onRegisterViewModel(viewModel: ExhibitionDetailViewModel) {
        viewModel.setExhibitionExhibition(exhibition)
    }

    override fun setupBindings(viewModel: ExhibitionDetailViewModel) {
        viewModel.title
                .subscribe {
                    expandedTitle.text = it
                    toolbarTitle.text = it
                }
                .disposedBy(disposeBag)

        viewModel.imageUrl
                .subscribe {
                    val options = RequestOptions()
                            .dontAnimate()
                            .dontTransform()
                    Glide.with(this)
//                            .asBitmap()
                            .load(it)
                            .apply(options)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?,
                                                          model: Any?,
                                                          target: Target<Drawable>?,
                                                          isFirstResource: Boolean)
                                        : Boolean {
                                    startPostponedEnterTransition()
                                    return false
                                }

                                override fun onResourceReady(
                                        resource: Drawable?,
                                        model: Any?,
                                        target: Target<Drawable>?,
                                        dataSource: DataSource?,
                                        isFirstResource: Boolean): Boolean {
                                    resource?.let {
                                        // Adds a nice animator to scale the container down to proper aspect ratio
                                        val parentWidth = (exhibitionImage.parent as View).width
                                        val newHeight = if (it.intrinsicWidth > it.intrinsicHeight) {
                                            ((it.intrinsicHeight.toFloat() / it.intrinsicWidth.toFloat()) * parentWidth).toInt()
                                        } else {
                                            parentWidth
                                        }
                                        if (newHeight != parentWidth) {
                                            val animator = ValueAnimator.ofInt(parentWidth, newHeight)
                                            animator.addUpdateListener {
                                                val params = exhibitionImage.layoutParams
                                                params.width = parentWidth
                                                params.height = it.animatedValue as Int
                                                exhibitionImage.layoutParams = params
                                            }
                                            animator.duration = 500
                                            animator.start()
                                        }

                                    }
                                    startPostponedEnterTransition()
                                    return false
                                }

                            }).into(exhibitionImage)
                }
                .disposedBy(disposeBag)

        viewModel.metaData
                .bindToMain(metaData.text())
                .disposedBy(disposeBag)

        viewModel.description
                .bindToMain(description.text())
                .disposedBy(disposeBag)

        viewModel.throughDate
                .bindToMain(throughDate.text())
                .disposedBy(disposeBag)

        viewModel.showOnMapButtonText
                .bindToMain(showOnMap.text())
                .disposedBy(disposeBag)

        viewModel.buyTicketsButtonText
                .bindToMain(buyTickets.text())
                .disposedBy(disposeBag)

        showOnMap.clicks()
                .subscribe { viewModel.onClickShowOnMap() }
                .disposedBy(disposeBag)

        buyTickets.clicks()
                .subscribe { viewModel.onClickBuyTickets() }
                .disposedBy(disposeBag)

    }

    override fun setupNavigationBindings(viewModel: ExhibitionDetailViewModel) {
        viewModel.navigateTo
                .subscribe {
                    when (it) {
                        is Navigate.Forward -> {
                            when (it.endpoint) {
                                is ExhibitionDetailViewModel.NavigationEndpoint.ShowOnMap -> {
                                    Timber.d("Show on map")
                                }

                                is ExhibitionDetailViewModel.NavigationEndpoint.BuyTickets -> {
                                    val endpoint = it.endpoint as ExhibitionDetailViewModel.NavigationEndpoint.BuyTickets
                                    var url = endpoint.url
                                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                                        url = "https://$url"
                                    val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(myIntent)
                                }
                            }
                        }
                        is Navigate.Back -> {

                        }
                    }
                }.disposedBy(disposeBag)
    }

    companion object {
        private val ARG_EXHIBITION = "${ExhibitionDetailFragment::class.java.simpleName}: exhibition"

        fun newInstance(exhibition: ArticExhibition) = ExhibitionDetailFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_EXHIBITION, exhibition)
            }
        }
    }
}
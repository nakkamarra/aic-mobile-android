package edu.artic.audio


import android.arch.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import edu.artic.viewmodel.ViewModelKey

/**
 * @author Sameer Dhakal (Fuzz)
 */
@Module
abstract class AudioModule {


    @Binds
    @IntoMap
    @ViewModelKey(AudioDetailsViewModel::class)
    abstract fun informationViewModel(audioDetailsViewModel: AudioDetailsViewModel): ViewModel

    @get:ContributesAndroidInjector
    abstract val audioDetailsFragment: AudioDetailsFragment

    @get:ContributesAndroidInjector
    abstract val audioSelectFragment: AudioSelectFragment

    @get:ContributesAndroidInjector
    abstract val audioActivity: AudioActivity

}

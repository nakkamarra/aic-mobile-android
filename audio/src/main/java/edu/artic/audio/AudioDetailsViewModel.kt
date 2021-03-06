package edu.artic.audio

import com.fuzz.rx.bindTo
import com.fuzz.rx.disposedBy
import edu.artic.db.models.ArticObject
import edu.artic.db.models.AudioFileModel
import edu.artic.db.models.audioFile
import edu.artic.localization.LanguageSelector
import edu.artic.viewmodel.BaseViewModel
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * This is where the [AudioDetailsFragment] gets its information about a specific
 * [ArticObject]. One such object may contain an [ArticAudioFile][edu.artic.db.models.ArticAudioFile],
 * and if it does so we populate fields in this class with that as a primary
 * source.
 *
 * @author Sameer Dhakal (Fuzz)
 * @see AudioFileModel
 */
class AudioDetailsViewModel @Inject constructor(val languageSelector: LanguageSelector) : BaseViewModel() {
    val title: Subject<String> = BehaviorSubject.create()
    val image: Subject<String> = BehaviorSubject.create()
    val availableTranslations: Subject<List<AudioFileModel>> = BehaviorSubject.create()
    val chosenAudioModel: Subject<AudioFileModel> = BehaviorSubject.create()
    val transcript: Subject<String> = BehaviorSubject.create()
    val credits: Subject<String> = BehaviorSubject.create()
    val authorCulturalPlace: Subject<String> = BehaviorSubject.create()

    private val objectObservable: Subject<ArticObject> = BehaviorSubject.create()


    var audioObject: ArticObject? = null
        set(value) {
            field = value
            value?.let {
                objectObservable.onNext(it)
            }
        }

    init {

        // Just bind all the properties that aren't specific to the audioModel so
        // that we don't need to worry about them later

        objectObservable
                .map {
                    it.artistCulturePlaceDelim?.replace("\r", "\n").orEmpty()
                }.bindTo(authorCulturalPlace)
                .disposedBy(disposeBag)

        objectObservable
                .map {
                    it.largeImageFullPath.orEmpty()
                }.bindTo(image)
                .disposedBy(disposeBag)

        // Note there is a 'credits' property on each AudioFileModel. We do not want that here.
        objectObservable
                .map {
                    // This is a credit line for the object, _not_ translated on a per-language basis
                    it.creditLine.orEmpty()
                }.bindTo(credits)
                .disposedBy(disposeBag)




        // Retrieve a list of all translations we have available for this object
        val known = objectObservable
                .map {
                    it.audioFile?.allTranslations().orEmpty()
                }.share()

        known.bindTo(availableTranslations)
                .disposedBy(disposeBag)

        // Set up the default language selection.
        known.map {
            // TODO: we'll need to modify this to account for the current Tour language
                    languageSelector.selectFrom(it)
                }.bindTo(chosenAudioModel)
                .disposedBy(disposeBag)


        // Lastly, we need to attach the translatable audio properties. These come from 'chosenAudioModel'.

        chosenAudioModel
                .map {
                    // TODO: default to articObject.title, perhaps?
                    it.title.orEmpty()
                }.bindTo(title)
                .disposedBy(disposeBag)
        chosenAudioModel
                .map {
                    it.transcript.orEmpty()
                }.bindTo(transcript)
                .disposedBy(disposeBag)


    }

    /**
     * This override lasts solely for the current object audio; it
     * does not transfer to other objects in the tour or persist
     * into app- or system-settings.
     */
    fun setTranslationOverride(audioModel: AudioFileModel) {
        chosenAudioModel.onNext(audioModel)
    }

}
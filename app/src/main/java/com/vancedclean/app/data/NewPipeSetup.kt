package com.vancedclean.app.data

import com.vancedclean.app.viewmodel.DownloaderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Inicializa NewPipe con el idioma español y el país elegido en Ajustes. */
object NewPipeSetup {

    @Volatile
    private var appliedCountry: String? = null

    fun ensure() {
        val country = CurrentSettings.value.country
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(DownloaderImpl.getInstance(), Localization("es", country), ContentCountry(country))
            appliedCountry = country
        } else if (appliedCountry != country) {
            NewPipe.setupLocalization(Localization("es", country), ContentCountry(country))
            appliedCountry = country
        }
        InnerTube.country = country
    }
}

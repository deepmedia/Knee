package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*

@OptIn(ExperimentalStdlibApi::class)
@CName(externName = "JNI_OnLoad")
@KneeInit
fun initKnee() {
    require(isExperimentalMM()) {
        "Not experimental MM"
    }
}

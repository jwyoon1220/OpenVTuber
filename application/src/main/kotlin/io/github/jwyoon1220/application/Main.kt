package io.github.jwyoon1220.application

import io.github.jwyoon1220.api.OpenVtuber
import io.github.jwyoon1220.core.DefaultOpenVtuber

fun main() {
    val application: OpenVtuber = DefaultOpenVtuber()
    application.start()
}

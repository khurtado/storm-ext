package com.stormunblessed.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class EmturbovidExtractor : ExtractorApi() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(url, referer = referer ?: "$mainUrl/")
        val playerScript =
            response.document.selectXpath("//script[contains(text(),'var urlPlay')]")
                .html()

        if (playerScript.isNotBlank()) {
            val m3u8Url =
                playerScript.substringAfter("var urlPlay = '").substringBefore("'")

            if (m3u8Url.isNotBlank()) {
                return M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = m3u8Url,
                    referer = response.url
                )
            }
        }
        return null
    }
}

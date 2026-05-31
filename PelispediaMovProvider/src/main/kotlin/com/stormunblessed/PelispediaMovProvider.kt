package com.stormunblessed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class PelispediaMovProvider : MainAPI() {
    override var mainUrl = "https://pelispedia.mov"
    override var name = "PelispediaMov"
    override var lang = "mx"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
    )

    override val mainPage = mainPageOf(
        "peliculas" to "Películas",
        "series" to "Series",
        "anime" to "Anime",
        "doramas" to "Doramas",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home = document.select("div.movie-card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("a") ?: return null
        val href = anchor.attr("href")
        val title = this.selectFirst("h4")?.text() ?: return null
        val img = this.selectFirst("img")?.attr("src")
        val year = this.selectFirst("div.nova-badge.year")?.text()?.toIntOrNull()
        val typeText = this.selectFirst("div.nova-badge.secondary")?.text()?.lowercase() ?: ""
        val tvType = when {
            "serie" in typeText -> TvType.TvSeries
            "anime" in typeText -> TvType.Anime
            "dorama" in typeText -> TvType.AsianDrama
            else -> TvType.Movie
        }
        return newMovieSearchResponse(title, fixUrl(href), tvType) {
            this.posterUrl = img
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?s=$query").document
        return document.select("div.movie-card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val tvType = when {
            "/pelicula/" in url -> TvType.Movie
            "/anime/" in url -> TvType.Anime
            "/dorama/" in url -> TvType.AsianDrama
            else -> TvType.TvSeries
        }

        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        val poster = doc.selectFirst("img.object-cover")?.attr("src")
        val description = doc.selectFirst("h1")
            ?.parent()
            ?.select("div.mb-6 h2")
            ?.firstOrNull()?.text()?.trim()

        val metaDiv = doc.selectFirst("h1")?.nextElementSibling()
        val dateText = metaDiv?.selectFirst("span.nova-badge.secondary")?.text()?.trim() ?: ""
        val year = Regex("(\\d{4})").find(dateText)?.groupValues?.get(1)?.toIntOrNull()

        val genreDiv = doc.selectFirst("h1")?.parent()?.select("div.mb-6 a[href*=/generos/]")
        val tags = genreDiv?.map { it.text().trim() } ?: emptyList()

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
            }
        }

        val episodes = ArrayList<Episode>()
        doc.select("a[href*=/temporada/][href*=/capitulo/]").forEach { a ->
            val href = a.attr("href")
            val regex = Regex("/temporada/(\\d+)/capitulo/(\\d+)")
            val match = regex.find(href)
            val seasonNum = match?.groupValues?.get(1)?.toIntOrNull()
            val epNum = match?.groupValues?.get(2)?.toIntOrNull()
            val epTitle = a.selectFirst("span.text-nova-text")?.text()?.trim()
            episodes.add(
                newEpisode(fixUrl(href)) {
                    this.name = epTitle
                    this.season = seasonNum
                    this.episode = epNum
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, tvType, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("div.player-content iframe").amap { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                if ("embed69" in src) {
                    Embed69Extractor.load(src, data, subtitleCallback, callback)
                } else {
                    loadExtractor(fixHostsLinks(src), data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}

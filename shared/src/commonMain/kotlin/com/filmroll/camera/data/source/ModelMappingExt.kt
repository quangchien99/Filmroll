package com.filmroll.camera.data.source

import com.filmroll.camera.FilmLut
import com.filmroll.camera.data.source.network.NetworkFilmLut


/*
Converts Network film lut response to Local [FilmLut]
 */
fun NetworkFilmLut.toLocal() = FilmLut(
    name = name,
    category = category,
    image_url = thumbnail,
    lut_name = lutFile
)

fun FilmLut.toFavoriteLut() = com.filmroll.camera.FavoriteLut(
    name = name,
    category = category,
    image_url = image_url,
    lut_name = lut_name
)

fun List<NetworkFilmLut>.toLocal() = map(NetworkFilmLut::toLocal)
package com.wanluk.libcomposeui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** SVG sources live in icons/; vectors keep rendering local and allocation-free. */
enum class ActionSymbol(@DrawableRes internal val drawable: Int) {
  SETTINGS(R.drawable.ic_settings),
  MIC(R.drawable.ic_mic),
  STOP(R.drawable.ic_stop),
  PLAY(R.drawable.ic_play),
  PREVIOUS(R.drawable.ic_previous),
  NEXT(R.drawable.ic_next),
  BACK(R.drawable.ic_back),
  FINISH(R.drawable.ic_finish),
  RERECORD(R.drawable.ic_rerecord),
  LIBRARY(R.drawable.ic_library),
  MORE(R.drawable.ic_more),
  MULTISELECT(R.drawable.ic_multiselect),
  ADD(R.drawable.ic_add),
  CLOSE(R.drawable.ic_close),
  SEARCH(R.drawable.ic_search),
  FILTER(R.drawable.ic_filter),
  QR(R.drawable.ic_qr),
  IMPORT(R.drawable.ic_import),
  EXPORT(R.drawable.ic_export),
  COPY(R.drawable.ic_copy),
  EDIT(R.drawable.ic_edit),
  TRASH(R.drawable.ic_trash),
  HISTORY(R.drawable.ic_history),
  NOTE(R.drawable.ic_note),
  PROFILE(R.drawable.ic_profile),
  SUN(R.drawable.ic_sun),
  MOON(R.drawable.ic_moon),
  SYSTEM(R.drawable.ic_system),
  WAVE(R.drawable.ic_wave),
  SHIELD(R.drawable.ic_shield),
  RECORDINGS(R.drawable.ic_recordings),
  HEADPHONES(R.drawable.ic_headphones),
  UP(R.drawable.ic_up),
  DOWN(R.drawable.ic_down),
  INFO(R.drawable.ic_info),
  IMAGE(R.drawable.ic_image);
}

@Composable
fun ActionIcon(symbol: ActionSymbol, modifier: Modifier = Modifier, contentDescription: String? = null) {
  Icon(painterResource(symbol.drawable), contentDescription, modifier.size(24.dp))
}

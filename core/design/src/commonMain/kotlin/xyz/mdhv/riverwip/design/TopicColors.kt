package xyz.mdhv.riverwip.design

import androidx.compose.ui.graphics.Color
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.TopicPalette

/**
 * Compose-facing view of the CVD-safe topic palette ([TopicPalette], `:core:model`).
 * **Never use this color alone to carry meaning** — every topic chip/legend entry
 * must also show the topic's text label (brief §2: colour is always paired with a
 * non-colour channel, since the primary user is red–green colourblind).
 */
fun Topic.toComposeColor(): Color = Color(TopicPalette.colorFor(this))

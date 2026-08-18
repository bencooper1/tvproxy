package com.tvproxy.app.data.epg

import org.xmlpull.v1.XmlPullParser

/**
 * Indirection over the XmlPullParser implementation: production binds
 * `android.util.Xml.newPullParser()`; JVM unit tests bind kxml2 (the Android pull
 * implementation is not available off-device, not even under Robolectric).
 */
fun interface PullParserProvider {
    fun newPullParser(): XmlPullParser
}

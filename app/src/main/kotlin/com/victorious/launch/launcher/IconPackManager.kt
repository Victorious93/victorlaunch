package com.victorious.launch.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.victorious.launch.core.launcher.IconPackContract
import org.xmlpull.v1.XmlPullParser

data class IconPackApp(
    val packageName: String,
    val label: String,
)

class IconPackManager(private val pm: PackageManager) {
    fun installedPacks(): List<IconPackApp> {
        val seen = LinkedHashMap<String, IconPackApp>()
        IconPackContract.THEME_ACTIONS.forEach { action ->
            val intent = Intent(action)
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val pkg = info.activityInfo.packageName
                if (pkg !in seen) {
                    seen[pkg] = IconPackApp(pkg, info.loadLabel(pm).toString())
                }
            }
        }
        return seen.values.toList()
    }

    fun loadComponentIcon(packPackage: String?, component: String): Drawable? {
        if (packPackage.isNullOrBlank()) return null
        val drawableName = appFilter(packPackage)[normalizeComponent(component)] ?: return null
        return try {
            val res = pm.getResourcesForApplication(packPackage)
            val id = res.getIdentifier(drawableName, "drawable", packPackage)
            if (id == 0) null else res.getDrawable(id, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun appFilter(packPackage: String): Map<String, String> {
        cache[packPackage]?.let { return it }
        val parsed = parseAppFilter(packPackage)
        cache[packPackage] = parsed
        return parsed
    }

    private fun parseAppFilter(packPackage: String): Map<String, String> {
        return try {
            val res = pm.getResourcesForApplication(packPackage)
            val id = res.getIdentifier("appfilter", "xml", packPackage)
            val parser =
                if (id != 0) {
                    res.getXml(id)
                } else {
                    pm.getResourcesForApplication(packPackage).assets.open("appfilter.xml").use { input ->
                        val pull = android.util.Xml.newPullParser()
                        pull.setInput(input, "UTF-8")
                        return readItems(pull)
                    }
                }
            readItems(parser)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readItems(parser: XmlPullParser): Map<String, String> {
        val out = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    out[normalizeComponent(component)] = drawable
                }
            }
            event = parser.next()
        }
        return out
    }

    companion object {
        private val cache = HashMap<String, Map<String, String>>()

        fun normalizeComponent(raw: String): String {
            val trimmed = raw.trim()
            val inner =
                if (trimmed.startsWith("ComponentInfo{") && trimmed.endsWith("}")) {
                    trimmed.removePrefix("ComponentInfo{").removeSuffix("}")
                } else {
                    trimmed
                }
            return inner.lowercase()
        }
    }
}

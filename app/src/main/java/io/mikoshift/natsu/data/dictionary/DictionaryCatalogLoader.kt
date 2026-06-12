package io.mikoshift.natsu.data.dictionary

import android.content.Context
import io.mikoshift.natsu.domain.model.DictionaryCatalogItem
import org.json.JSONObject

class DictionaryCatalogLoader(
    private val context: Context,
) {
    fun loadCatalog(): List<DictionaryCatalogItem> {
        val jsonText = context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)
        val terms = root.getJSONObject("ja").getJSONArray("terms")
        return buildList {
            for (index in 0 until terms.length()) {
                val item = terms.getJSONObject(index)
                val downloadUrl = item.getString("downloadUrl")
                DictionaryUrlValidator.validateDownloadUrl(downloadUrl)
                add(
                    DictionaryCatalogItem(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        description = item.getString("description"),
                        downloadUrl = downloadUrl,
                        sizeHintMb = item.optInt("sizeHintMb", 0),
                    ),
                )
            }
        }
    }

    companion object {
        private const val CATALOG_ASSET = "dictionary_catalog.json"
    }
}

package com.gamal.randompicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(var text: String, var used: Boolean = false)
data class PickList(var name: String, val items: MutableList<Item> = mutableListOf())

/** Saves all lists as JSON in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("picker", Context.MODE_PRIVATE)

    val lists: MutableList<PickList> = mutableListOf()
    var current: Int = 0
    var noRepeat: Boolean = false

    init { load() }

    val currentList: PickList get() = lists[current]

    private fun load() {
        val raw = prefs.getString("lists", null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val list = PickList(o.getString("name"))
                val items = o.getJSONArray("items")
                for (j in 0 until items.length()) {
                    val it = items.getJSONObject(j)
                    list.items.add(Item(it.getString("t"), it.optBoolean("u", false)))
                }
                lists.add(list)
            }
        }
        current = prefs.getInt("current", 0)
        noRepeat = prefs.getBoolean("noRepeat", false)
    }

    fun ensureOneList(defaultName: String) {
        if (lists.isEmpty()) lists.add(PickList(defaultName))
        if (current !in lists.indices) current = 0
    }

    fun save() {
        val arr = JSONArray()
        for (l in lists) {
            val items = JSONArray()
            for (it in l.items) items.put(JSONObject().put("t", it.text).put("u", it.used))
            arr.put(JSONObject().put("name", l.name).put("items", items))
        }
        prefs.edit()
            .putString("lists", arr.toString())
            .putInt("current", current)
            .putBoolean("noRepeat", noRepeat)
            .apply()
    }
}

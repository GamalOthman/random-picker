package com.gamal.randompicker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ItemAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var resultText: TextView
    private lateinit var countText: TextView
    private lateinit var pickBtn: MaterialButton
    private lateinit var resetBtn: MaterialButton
    private lateinit var input: TextInputEditText
    private lateinit var emptyText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var spinning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(this)
        store.ensureOneList(getString(R.string.default_list_name))

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnClickListener { showListChooser() }

        resultText = findViewById(R.id.resultText)
        countText = findViewById(R.id.countText)
        pickBtn = findViewById(R.id.pickBtn)
        resetBtn = findViewById(R.id.resetBtn)
        input = findViewById(R.id.itemInput)
        emptyText = findViewById(R.id.emptyText)

        adapter = ItemAdapter(
            getItems = { store.currentList.items },
            onDelete = { pos -> deleteItem(pos) },
            onLongPress = { pos -> editItem(pos) },
            onTap = { pos ->
                // Tap an item to mark/unmark it as already picked
                val item = store.currentList.items[pos]
                item.used = !item.used
                adapter.notifyItemChanged(pos)
                saveAndRefresh()
            },
        )
        findViewById<RecyclerView>(R.id.itemsList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<MaterialButton>(R.id.addBtn).setOnClickListener { addItem() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addItem(); true } else false
        }

        val noRepeatSwitch = findViewById<MaterialSwitch>(R.id.noRepeatSwitch)
        noRepeatSwitch.isChecked = store.noRepeat
        noRepeatSwitch.setOnCheckedChangeListener { _, checked ->
            store.noRepeat = checked
            saveAndRefresh()
        }

        pickBtn.setOnClickListener { startSpin() }
        resetBtn.setOnClickListener { resetUsed() }

        refresh()
    }

    // ---------- items ----------

    private fun addItem() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        // Allow several items at once, separated by commas or new lines
        val parts = text.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val list = store.currentList.items
        val start = list.size
        parts.forEach { list.add(Item(it)) }
        adapter.notifyItemRangeInserted(start, parts.size)
        input.setText("")
        saveAndRefresh()
    }

    private fun deleteItem(pos: Int) {
        if (spinning) return
        val list = store.currentList
        val removed = list.items.removeAt(pos)
        adapter.notifyItemRemoved(pos)
        saveAndRefresh()
        Snackbar.make(findViewById(android.R.id.content), R.string.item_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                // Put it back only if the same list is still shown
                if (store.currentList === list) {
                    val at = pos.coerceAtMost(list.items.size)
                    list.items.add(at, removed)
                    adapter.notifyItemInserted(at)
                    saveAndRefresh()
                }
            }
            .show()
    }

    private fun editItem(pos: Int) {
        if (spinning) return
        val item = store.currentList.items[pos]
        askName(R.string.edit_item, item.text) { text ->
            item.text = text
            adapter.notifyDataSetChanged()
            saveAndRefresh()
        }
    }

    private fun resetUsed() {
        store.currentList.items.forEach { it.used = false }
        adapter.notifyDataSetChanged()
        resultText.text = getString(R.string.tap_pick)
        saveAndRefresh()
    }

    // ---------- picking ----------

    private fun pool(): List<Item> {
        val items = store.currentList.items
        return if (store.noRepeat) items.filter { !it.used } else items
    }

    private fun startSpin() {
        if (spinning) return
        val pool = pool()
        if (store.currentList.items.isEmpty()) {
            toast(getString(R.string.add_items_first)); return
        }
        if (pool.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.all_picked)
                .setPositiveButton(R.string.reset) { _, _ -> resetUsed() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val winner = pool[Random.nextInt(pool.size)]
        spinning = true
        pickBtn.isEnabled = false

        // Flash random names, slowing down, then land on the winner
        var delay = 40L
        fun step() {
            if (delay > 320L || pool.size == 1) {
                land(winner); return
            }
            resultText.text = pool[Random.nextInt(pool.size)].text
            resultText.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            delay = (delay * 1.15).toLong() + 4
            handler.postDelayed({ step() }, delay)
        }
        resultText.animate().cancel()
        resultText.scaleX = 1f; resultText.scaleY = 1f
        step()
    }

    private fun land(winner: Item) {
        resultText.text = winner.text
        resultText.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        resultText.scaleX = 0.6f; resultText.scaleY = 0.6f
        resultText.animate().scaleX(1f).scaleY(1f).setDuration(350)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()

        if (store.noRepeat) {
            winner.used = true
            adapter.notifyItemChanged(store.currentList.items.indexOf(winner))
        }
        spinning = false
        pickBtn.isEnabled = true
        saveAndRefresh()
    }

    // ---------- lists ----------

    private fun showListChooser() {
        if (spinning) return
        val names = store.lists.map { "${it.name}  (${it.items.size})" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_list)
            .setSingleChoiceItems(names, store.current) { d, which ->
                switchTo(which); d.dismiss()
            }
            .setPositiveButton(R.string.new_list) { _, _ -> newList() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun switchTo(index: Int) {
        store.current = index
        resultText.text = getString(R.string.tap_pick)
        adapter.notifyDataSetChanged()
        saveAndRefresh()
    }

    private fun askName(title: Int, initial: String, onOk: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(initial); setSelection(initial.length); setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0); addView(field)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotEmpty()) onOk(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        field.requestFocus()
    }

    private fun newList() = askName(R.string.new_list, "") { name ->
        store.lists.add(PickList(name))
        switchTo(store.lists.lastIndex)
    }

    private fun renameList() = askName(R.string.rename_list, store.currentList.name) { name ->
        store.currentList.name = name
        saveAndRefresh()
    }

    private fun deleteList() {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.delete_list_confirm, store.currentList.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.lists.removeAt(store.current)
                store.ensureOneList(getString(R.string.default_list_name))
                switchTo(store.current.coerceAtMost(store.lists.lastIndex))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearItems() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.clear_items_confirm)
            .setPositiveButton(R.string.clear) { _, _ ->
                store.currentList.items.clear()
                switchTo(store.current)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- language ----------

    private fun chooseLanguage() {
        val tags = arrayOf("", "en", "ar")
        val labels = arrayOf(getString(R.string.lang_default), "English", "العربية")
        val now = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val selected = tags.indexOfFirst { it.isNotEmpty() && now.startsWith(it) }.let { if (it < 0) 0 else it }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, selected) { d, which ->
                d.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (spinning) return true
        when (item.itemId) {
            R.id.action_lists -> showListChooser()
            R.id.action_new -> newList()
            R.id.action_rename -> renameList()
            R.id.action_delete -> deleteList()
            R.id.action_clear -> clearItems()
            R.id.action_language -> chooseLanguage()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------- helpers ----------

    private fun saveAndRefresh() { store.save(); refresh() }

    private fun refresh() {
        val list = store.currentList
        toolbar.title = list.name
        val total = list.items.size
        val left = list.items.count { !it.used }
        countText.visibility = if (total == 0) android.view.View.GONE else android.view.View.VISIBLE
        countText.text = if (store.noRepeat)
            getString(R.string.count_left, left, total)
        else
            resources.getQuantityString(R.plurals.count_items, total, total)
        resetBtn.isEnabled = list.items.any { it.used }
        emptyText.visibility = if (total == 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

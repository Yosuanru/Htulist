package com.yosuanru.htulist.ui.todo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yosuanru.htulist.R
import com.yosuanru.htulist.data.AppDatabase
import com.yosuanru.htulist.data.entity.TaskItem
import com.yosuanru.htulist.data.entity.TaskProject
import com.yosuanru.htulist.databinding.ActivityTodoBinding
import com.yosuanru.htulist.ui.ScheduleReturnSource
import com.yosuanru.htulist.ui.importdata.ImportActivity
import com.yosuanru.htulist.ui.playNavigationMotion
import com.yosuanru.htulist.ui.returnToSchedule
import com.yosuanru.htulist.ui.settings.SettingsActivity
import com.yosuanru.htulist.ui.selectItemWithoutAnimation
import com.yosuanru.htulist.ui.stabilizeActiveIndicatorSize
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办清单。
 *
 * 数据模型参考 Todometer（Apache-2.0），但按本应用需求做了简化：
 * 三态（待办/进行中/完成）简化为勾选两态，去掉标签，补上置顶与到期时间。
 *
 * 分组（清单）用顶部 Chip 切换，长按 Chip 可重命名 / 删除。
 */
class TodoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTodoBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter

    private var allTasks: List<TaskItem> = emptyList()
    private var projects: List<TaskProject> = emptyList()
    private var selectedProjectId: Long = TaskItem.DEFAULT_PROJECT_ID
    private var suppressBottomNavigationMotion = false

    private val dueFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())

    /** 与设置页/导入页同一套入场缓动，保证三页观感一致 */
    private val motionInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** 入场动画只放一次；列表项的错峰淡入也只在首次拿到数据时放 */
    private var entrancePlayed = false
    private var listEntrancePlayed = false
    private var emptyStateFadedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 返回键同样回到课表主页，且带和其它页面一致的返回过场动画
        onBackPressedDispatcher.addCallback(this) { returnToSchedule(ScheduleReturnSource.TODO) }
        binding = ActivityTodoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        adapter = TaskAdapter(
            onToggleDone = ::toggleDone,
            onTogglePin = ::togglePin,
            onOpen = { task -> showEditor(task) }
        )

        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter
        attachSwipeToDelete()

        binding.fabAdd.setOnClickListener { showEditor(null) }

        setUpBottomNavigation()
        observeData()
        animateTodoEntrance()
    }

    // ---------------- 入场动画 ----------------

    /**
     * 与「设置」「导入课程」两页一样的错峰淡入。
     * 空列表和非空列表都要放 —— 之前这两页有动画而这页什么都没有，看着很割裂。
     */
    private fun animateTodoEntrance() {
        if (entrancePlayed) return
        entrancePlayed = true

        val chips = binding.chipProjects
        val list = binding.recyclerTasks
        val fab = binding.fabAdd

        // 起始状态。注意不要把 emptyState 算进来 —— 它在入场这会儿是 GONE，
        // 动画会把 alpha 停在 0.18 却到不了 1，等它之后显示出来就永远是半透明的。
        // 空状态单独在 applyFilter 里淡入。
        listOf<View>(chips, list).forEach {
            it.alpha = 0.18f
            it.translationY = dp(26f)
        }
        fab.alpha = 0f
        fab.scaleX = 0.2f
        fab.scaleY = 0.2f

        binding.root.doOnPreDraw {
            listOf(chips to 0L, list to 70L).forEach { (view, delay) ->
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(560L)
                    .setInterpolator(motionInterpolator)
                    .withLayer()
                    .start()
            }
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(150L)
                .setDuration(480L)
                .setInterpolator(motionInterpolator)
                .withLayer()
                .start()
        }
    }

    /** 列表项逐个错峰淡入。只在第一次真正拿到数据时放一次。 */
    private fun animateTaskItems() {
        binding.recyclerTasks.post {
            val count = minOf(binding.recyclerTasks.childCount, 10)
            for (i in 0 until count) {
                val item = binding.recyclerTasks.getChildAt(i) ?: continue
                item.alpha = 0f
                item.translationY = dp(18f)
                item.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(90L + i * 42L)
                    .setDuration(440L)
                    .setInterpolator(motionInterpolator)
                    .withLayer()
                    .start()
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    // ---------------- 数据 ----------------

    private fun observeData() {
        db.taskProjectDao().getAll().observe(this) { list ->
            projects = list
            // 选中的分组被删掉了就回到第一个
            if (projects.none { it.id == selectedProjectId }) {
                selectedProjectId = projects.firstOrNull()?.id ?: TaskItem.DEFAULT_PROJECT_ID
            }
            renderProjectChips()
        }

        // 一次取全量、按分组在本地过滤：避免每次切分组都重新 observe，
        // 排序仍然由 SQL 里的 ORDER BY 保证。
        db.taskDao().getAll().observe(this) { list ->
            allTasks = list
            applyFilter()
        }
    }

    private fun applyFilter() {
        val shown = allTasks.filter { it.projectId == selectedProjectId }
        adapter.submitList(shown) {
            // 列表首次真正有内容时，让条目逐个浮现（只在第一次，之后增删不再重放）
            if (!listEntrancePlayed && shown.isNotEmpty()) {
                listEntrancePlayed = true
                animateTaskItems()
            }
        }

        val empty = shown.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerTasks.visibility = if (empty) View.GONE else View.VISIBLE

        // 空状态第一次露脸时淡入一次
        if (empty && !emptyStateFadedIn) {
            emptyStateFadedIn = true
            binding.emptyState.alpha = 0f
            binding.emptyState.translationY = dp(16f)
            binding.emptyState.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L)
                .setDuration(520L)
                .setInterpolator(motionInterpolator)
                .withLayer()
                .start()
        }
    }

    // ---------------- 分组 ----------------

    private fun renderProjectChips() {
        binding.chipProjects.removeAllViews()

        projects.forEach { project ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = project.name
                isCheckable = true
                isChecked = project.id == selectedProjectId
                tag = project.id
                setOnClickListener { selectProject(project.id) }
                setOnLongClickListener { showProjectMenu(project); true }
            }
            binding.chipProjects.addView(chip)
        }

        binding.chipProjects.addView(
            Chip(this).apply {
                text = "+ " + getString(R.string.todo_project_new)
                isCheckable = false
                setOnClickListener { promptNewProject() }
            }
        )
    }

    private fun selectProject(projectId: Long) {
        selectedProjectId = projectId
        for (i in 0 until binding.chipProjects.childCount) {
            val child = binding.chipProjects.getChildAt(i)
            if (child is Chip && child.isCheckable) {
                child.isChecked = child.tag == projectId
            }
        }
        applyFilter()
    }

    private fun promptNewProject() {
        val input = TextInputEditText(this).apply {
            hint = getString(R.string.todo_project_name_hint)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.todo_project_new)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.todo_save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val id = db.taskProjectDao().insert(
                        TaskProject(name = name, sortOrder = projects.size)
                    )
                    selectProject(id)
                }
            }
            .show()
    }

    private fun showProjectMenu(project: TaskProject) {
        MaterialAlertDialogBuilder(this)
            .setTitle(project.name)
            .setItems(
                arrayOf(
                    getString(R.string.todo_project_rename),
                    getString(R.string.todo_project_delete)
                )
            ) { _, which ->
                when (which) {
                    0 -> promptRenameProject(project)
                    1 -> confirmDeleteProject(project)
                }
            }
            .show()
    }

    private fun promptRenameProject(project: TaskProject) {
        val input = TextInputEditText(this).apply {
            setText(project.name)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.todo_project_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.todo_save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch { db.taskProjectDao().update(project.copy(name = name)) }
            }
            .show()
    }

    private fun confirmDeleteProject(project: TaskProject) {
        if (projects.size <= 1) {
            toast("至少保留一个清单")
            return
        }
        val fallback = projects.first { it.id != project.id }
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.todo_project_delete_confirm, project.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.todo_delete) { _, _ ->
                lifecycleScope.launch {
                    // 先把条目挪走再删分组，避免留下孤儿数据
                    db.taskDao().moveAllToProject(project.id, fallback.id)
                    db.taskProjectDao().delete(project)
                    if (selectedProjectId == project.id) selectProject(fallback.id)
                }
            }
            .show()
    }

    // ---------------- 条目操作 ----------------

    private fun toggleDone(task: TaskItem) {
        lifecycleScope.launch {
            val nowDone = !task.isDone
            db.taskDao().setDone(
                task.id,
                nowDone,
                if (nowDone) System.currentTimeMillis() else null
            )
        }
    }

    private fun togglePin(task: TaskItem) {
        lifecycleScope.launch { db.taskDao().setPinned(task.id, !task.isPinned) }
    }

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val task = adapter.currentList.getOrNull(position) ?: return
                // 划走后先把行恢复原样：确认框里点取消时，数据没变但视图已经划没了
                adapter.notifyItemChanged(position)
                confirmDeleteTask(task)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerTasks)
    }

    private fun confirmDeleteTask(task: TaskItem) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.todo_delete_confirm, task.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.todo_delete) { _, _ ->
                lifecycleScope.launch {
                    db.taskDao().deleteById(task.id)
                    toast(getString(R.string.todo_delete_done))
                }
            }
            .show()
    }

    // ---------------- 新建 / 编辑 ----------------

    /** [task] 为 null 表示新建 */
    private fun showEditor(task: TaskItem?) {
        val view = layoutInflater.inflate(R.layout.dialog_task_edit, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        val tilTitle = view.findViewById<TextInputLayout>(R.id.tilTitle)
        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)
        val rowDue = view.findViewById<View>(R.id.rowDue)
        val tvDue = view.findViewById<android.widget.TextView>(R.id.tvDue)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDelete)

        var dueAt: Long? = task?.dueAt

        fun renderDue() {
            tvDue.text = dueAt?.let { dueFormat.format(Date(it)) }
                ?: getString(R.string.todo_due_none)
        }

        etTitle.setText(task?.title.orEmpty())
        etNote.setText(task?.note.orEmpty())
        renderDue()
        btnDelete.visibility = if (task == null) View.GONE else View.VISIBLE

        rowDue.setOnClickListener {
            if (dueAt == null) {
                openDatePicker(null) { picked -> dueAt = picked; renderDue() }
            } else {
                MaterialAlertDialogBuilder(this)
                    .setItems(
                        arrayOf(
                            getString(R.string.todo_due),
                            getString(R.string.todo_due_clear)
                        )
                    ) { _, which ->
                        if (which == 0) {
                            openDatePicker(dueAt) { picked -> dueAt = picked; renderDue() }
                        } else {
                            dueAt = null
                            renderDue()
                        }
                    }
                    .show()
            }
        }

        btnSave.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                tilTitle.error = getString(R.string.todo_title_required)
                return@setOnClickListener
            }
            tilTitle.error = null
            val note = etNote.text?.toString().orEmpty()

            lifecycleScope.launch {
                if (task == null) {
                    db.taskDao().insert(
                        TaskItem(
                            title = title,
                            note = note,
                            projectId = selectedProjectId,
                            dueAt = dueAt
                        )
                    )
                } else {
                    db.taskDao().update(task.copy(title = title, note = note, dueAt = dueAt))
                }
                dialog.dismiss()
            }
        }

        btnDelete.setOnClickListener {
            val target = task ?: return@setOnClickListener
            dialog.dismiss()
            confirmDeleteTask(target)
        }

        dialog.show()
    }

    private fun openDatePicker(current: Long?, onPicked: (Long?) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.todo_due)
            .setSelection(current ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(it) }
        picker.show(supportFragmentManager, "todo_due_picker")
    }

    // ---------------- 底部导航 ----------------

    private fun setUpBottomNavigation() {
        binding.bottomNavigation.stabilizeActiveIndicatorSize()
        binding.bottomNavigation.selectItemWithoutAnimation(R.id.nav_todo)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (suppressBottomNavigationMotion) return@setOnItemSelectedListener true
            val itemView = binding.bottomNavigation.findViewById<View>(item.itemId)
            when (item.itemId) {
                R.id.nav_home -> {
                    // 和设置页/导入页走同一条返回通道，课表主页才会放同一套错峰入场动画
                    returnToSchedule(ScheduleReturnSource.TODO)
                    true
                }
                R.id.nav_import -> {
                    switchTo(ImportActivity::class.java)
                    true
                }
                R.id.nav_settings -> {
                    switchTo(SettingsActivity::class.java)
                    true
                }
                R.id.nav_todo -> {
                    itemView.playNavigationMotion()
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            binding.bottomNavigation.findViewById<View>(item.itemId)?.playNavigationMotion()
        }
        binding.bottomNavigation.post {
            binding.bottomNavigation.findViewById<View>(R.id.nav_todo)?.playNavigationMotion()
        }
    }

    private fun switchTo(activity: Class<*>) {
        startActivity(Intent(this, activity))
        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        if (binding.bottomNavigation.selectedItemId != R.id.nav_todo) {
            suppressBottomNavigationMotion = true
            binding.bottomNavigation.selectItemWithoutAnimation(R.id.nav_todo)
            suppressBottomNavigationMotion = false
        }
    }
}

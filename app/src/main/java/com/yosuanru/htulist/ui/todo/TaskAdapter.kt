package com.yosuanru.htulist.ui.todo

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yosuanru.htulist.R
import com.yosuanru.htulist.data.entity.TaskItem
import com.yosuanru.htulist.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 待办列表适配器。
 *
 * 勾选框用 ImageView 按状态换 drawable，而不是 CheckBox ——
 * 部分国产 ROM 会强制给 CheckBox 的 button 上 tint，把自绘的圆形两态图标染成单色。
 */
class TaskAdapter(
    private val onToggleDone: (TaskItem) -> Unit,
    private val onTogglePin: (TaskItem) -> Unit,
    private val onOpen: (TaskItem) -> Unit
) : ListAdapter<TaskItem, TaskAdapter.TaskViewHolder>(DIFF) {

    private val dueFormat = SimpleDateFormat("M月d日", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
        TaskViewHolder(
            ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskItem) {
            val context = binding.root.context

            // ---- 圆形勾选框 ----
            binding.ivCheck.setImageResource(
                if (task.isDone) R.drawable.ic_task_check_done
                else R.drawable.ic_task_check_circle
            )
            binding.ivCheck.setOnClickListener { onToggleDone(task) }

            // ---- 标题：完成后加删除线并降低对比 ----
            binding.tvTaskTitle.text = task.title
            binding.tvTaskTitle.paintFlags = if (task.isDone) {
                binding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.tvTaskTitle.alpha = if (task.isDone) 0.45f else 1f

            // ---- 多行备注（列表里只显示前两行，完整内容在编辑面板里看）----
            if (task.note.isBlank()) {
                binding.tvTaskNote.visibility = android.view.View.GONE
            } else {
                binding.tvTaskNote.visibility = android.view.View.VISIBLE
                binding.tvTaskNote.text = task.note
                binding.tvTaskNote.alpha = if (task.isDone) 0.5f else 1f
            }

            // ---- 到期时间 ----
            val due = task.dueAt
            if (due == null) {
                binding.tvTaskMeta.visibility = android.view.View.GONE
            } else {
                binding.tvTaskMeta.visibility = android.view.View.VISIBLE
                val overdue = !task.isDone && due < System.currentTimeMillis()
                binding.tvTaskMeta.text = context.getString(
                    R.string.todo_due_format, dueFormat.format(Date(due))
                )
                binding.tvTaskMeta.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (overdue) R.color.error else R.color.text_tertiary
                    )
                )
            }

            // ---- 置顶 ----
            binding.btnPin.setImageResource(R.drawable.ic_pin)
            binding.btnPin.imageTintList = ContextCompat.getColorStateList(
                context,
                if (task.isPinned) R.color.primary else R.color.text_tertiary
            )
            binding.btnPin.contentDescription = context.getString(
                if (task.isPinned) R.string.todo_unpin else R.string.todo_pin
            )
            binding.btnPin.setOnClickListener { onTogglePin(task) }
            // 置顶的条目给个浅色底，一眼能分出来
            binding.root.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (task.isPinned) R.color.primary_container else R.color.surface
                )
            )

            // ---- 点整行进入编辑 ----
            binding.root.setOnClickListener { onOpen(task) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TaskItem>() {
            override fun areItemsTheSame(oldItem: TaskItem, newItem: TaskItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TaskItem, newItem: TaskItem) =
                oldItem == newItem
        }
    }
}

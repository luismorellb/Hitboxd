package com.hitboxd.app.common.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hitboxd.app.R
import androidx.recyclerview.widget.RecyclerView
import com.hitboxd.app.data.model.Review
import com.hitboxd.app.utils.DateUtils

// ─── AUTH DIALOG ─────────────────────────────────────────
// Contiene 2 tabs: Sign In (usa username, NO email) y Create Account
class AuthDialogFragment : DialogFragment() {

    companion object {
        const val VIEW_LOGIN    = 0
        const val VIEW_REGISTER = 1

        fun newInstance(
            startPage: Int = VIEW_LOGIN,
            onLogin: (username: String, password: String) -> Unit,
            onRegister: (username: String, email: String, password: String) -> Unit
        ) = AuthDialogFragment().also {
            it.startPage  = startPage
            it.onLogin    = onLogin
            it.onRegister = onRegister
        }
    }

    var startPage: Int = VIEW_LOGIN
    var onLogin: ((String, String) -> Unit)? = null
    var onRegister: ((String, String, String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_auth, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerAuth)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutAuth)

        // Inflar las dos páginas correctamente con el parent para que respeten MATCH_PARENT
        val loginView    = layoutInflater.inflate(R.layout.dialog_auth_login, viewPager, false)
        val registerView = layoutInflater.inflate(R.layout.dialog_auth_register, viewPager, false)
        val pages        = listOf(loginView, registerView)

        // Adapter simple con vistas pre-infladas
        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = pages.size
            override fun getItemViewType(pos: Int) = pos
            override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                object : RecyclerView.ViewHolder(pages[type]) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {}
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = if (pos == VIEW_LOGIN) "Sign In" else "Create Account"
        }.attach()

        viewPager.currentItem = startPage

        // ── Login ──
        loginView.findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val username = loginView.findViewById<EditText>(R.id.etUsername).text.toString().trim()
            val password = loginView.findViewById<EditText>(R.id.etPassword).text.toString()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onLogin?.invoke(username, password)
            dismiss()
        }

        // ── Register ──
        registerView.findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val username = registerView.findViewById<EditText>(R.id.etUsername).text.toString().trim()
            val email    = registerView.findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val password = registerView.findViewById<EditText>(R.id.etPassword).text.toString()
            if (username.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onRegister?.invoke(username, email, password)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ─── REVIEW DIALOG ───────────────────────────────────────
// Crear reseña: texto + RatingBar + checkbox spoiler
class ReviewDialogFragment(
    private val onSubmit: (content: String, rating: Float, hasSpoilers: Boolean) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_review, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etContent  = view.findViewById<EditText>(R.id.etContent)
        val ratingBar  = view.findViewById<RatingBar>(R.id.ratingBar)
        val cbSpoilers = view.findViewById<CheckBox>(R.id.cbSpoilers)
        val btnSubmit  = view.findViewById<Button>(R.id.btnSubmit)
        val btnCancel  = view.findViewById<Button>(R.id.btnCancel)

        btnCancel.setOnClickListener { dismiss() }

        btnSubmit.setOnClickListener {
            val content = etContent.text.toString().trim()
            if (content.isBlank()) {
                etContent.error = "Escribe algo"
                return@setOnClickListener
            }
            if (ratingBar.rating == 0f) {
                Toast.makeText(context, "Selecciona un rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSubmit(content, ratingBar.rating, cbSpoilers.isChecked)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ─── REPORT DIALOG ───────────────────────────────────────
// RadioGroup con razones de reporte
class ReportDialogFragment : DialogFragment() {

    companion object {
        fun newInstance(reviewId: Int, onReport: (reason: String) -> Unit) =
            ReportDialogFragment().also { it.reviewId = reviewId; it.onReport = onReport }
    }

    var reviewId: Int = 0
    var onReport: ((String) -> Unit)? = null

    private val reasons = listOf("spam", "spoiler", "offensive", "off-topic", "other")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupReasons)

        reasons.forEach { reason ->
            RadioButton(requireContext()).apply {
                text = reason.replaceFirstChar { it.uppercase() }
                id   = View.generateViewId()
                tag  = reason
                radioGroup.addView(this)
            }
        }

        view.findViewById<Button>(R.id.btnReport).setOnClickListener {
            val checkedId = radioGroup.checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, "Selecciona un motivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val reason = radioGroup.findViewById<RadioButton>(checkedId).tag as String
            onReport?.invoke(reason)
            dismiss()
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ─── CREATE LIST DIALOG ──────────────────────────────────
// Crear lista nueva: título + descripción opcional
class CreateListDialogFragment(
    private val onCreate: (title: String, description: String?) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_create_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDesc  = view.findViewById<EditText>(R.id.etDescription)

        view.findViewById<Button>(R.id.btnCreate).setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isBlank()) { etTitle.error = "El título es requerido"; return@setOnClickListener }
            val desc = etDesc.text.toString().trim().takeIf { it.isNotBlank() }
            onCreate(title, desc)
            dismiss()
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ─── ADD GAME TO LIST DIALOG ─────────────────────────────
// Agregar juego a lista existente: solo comentario opcional
class AddGameToListDialogFragment : DialogFragment() {

    companion object {
        fun newInstance(gameId: Int, onAdd: (comment: String?) -> Unit) =
            AddGameToListDialogFragment().also { it.gameId = gameId; it.onAdd = onAdd }
    }

    var gameId: Int = 0
    var onAdd: ((String?) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_add_game_to_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etComment = view.findViewById<EditText>(R.id.etComment)

        view.findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val comment = etComment.text.toString().trim().takeIf { it.isNotBlank() }
            onAdd?.invoke(comment)
            dismiss()
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ─── CONFIRM DELETE DIALOG ───────────────────────────────
// Diálogo genérico de confirmación (eliminar lista, cuenta, etc.)
class ConfirmDeleteDialogFragment(
    private val message: String,
    private val confirmText: String = "Eliminar",
    private val onConfirm: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(confirmText) { _, _ -> onConfirm() }
            .setNegativeButton("Cancelar", null)
            .create()
    }
}

// ─── REVIEW DETAIL DIALOG ────────────────────────────────
// Vista completa de una reseña reportada: contenido, razones, metadatos
class ReviewDetailDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_ID      = "idReview"
        private const val ARG_GAME    = "gameTitle"
        private const val ARG_USER    = "username"
        private const val ARG_CONTENT = "content"
        private const val ARG_DATE    = "createdAt"
        private const val ARG_COUNT   = "reportCount"
        private const val ARG_REASONS = "allReasons"

        fun newInstance(review: Review) = ReviewDetailDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_ID,      review.idReview)
                putString(ARG_GAME,    review.gameTitle ?: "—")
                putString(ARG_USER,    review.username  ?: "—")
                putString(ARG_CONTENT, review.content   ?: "—")
                putString(ARG_DATE,    review.createdAt)
                putInt(ARG_COUNT,   review.reportCount)
                putString(ARG_REASONS, review.allReasons ?: "—")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_review_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        view.findViewById<TextView>(R.id.tvDetailReviewId).text =
            getString(R.string.format_hash_number, args.getInt(ARG_ID))
        view.findViewById<TextView>(R.id.tvDetailGame).text =
            args.getString(ARG_GAME)
        view.findViewById<TextView>(R.id.tvDetailUser).text =
            args.getString(ARG_USER)
        view.findViewById<TextView>(R.id.tvDetailDate).text =
            DateUtils.format(args.getString(ARG_DATE))
        view.findViewById<TextView>(R.id.tvDetailReportCount).text =
            args.getInt(ARG_COUNT).toString()
        view.findViewById<TextView>(R.id.tvDetailContent).text =
            args.getString(ARG_CONTENT)
        view.findViewById<TextView>(R.id.tvDetailReasons).text =
            args.getString(ARG_REASONS)
        view.findViewById<Button>(R.id.btnClose).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

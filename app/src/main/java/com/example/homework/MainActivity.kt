package com.example.MainActivity.kt

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.homework.R


data class Book(
    var id: Long = 0,
    var title: String,
    var author: String,
    var genre: String? = null
)

class BookDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "BookLibrary.db"
        private const val TABLE_BOOKS = "books"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_AUTHOR = "author"
        private const val COLUMN_GENRE = "genre"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_BOOKS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_GENRE TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKS")
        onCreate(db)
    }

    fun addBook(book: Book): Long {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put(COLUMN_TITLE, book.title)
                put(COLUMN_AUTHOR, book.author)
                put(COLUMN_GENRE, book.genre)
            }
            return db.insert(TABLE_BOOKS, null, values)
        }
    }

    fun getAllBooks(): List<Book> {
        val books = mutableListOf<Book>()
        readableDatabase.use { db ->
            val cursor = db.query(TABLE_BOOKS, null, null, null, null, null, "$COLUMN_TITLE ASC")
            cursor.use {
                while (it.moveToNext()) {
                    books.add(
                        Book(
                            id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                            title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                            author = it.getString(it.getColumnIndexOrThrow(COLUMN_AUTHOR)),
                            genre = it.getString(it.getColumnIndexOrThrow(COLUMN_GENRE))
                        )
                    )
                }
            }
        }
        return books
    }

    fun getBook(id: Long): Book? {
        readableDatabase.use { db ->
            val cursor = db.query(TABLE_BOOKS, null, "$COLUMN_ID=?", arrayOf(id.toString()), null, null, null)
            cursor.use {
                if (it.moveToFirst()) {
                    return Book(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                        author = it.getString(it.getColumnIndexOrThrow(COLUMN_AUTHOR)),
                        genre = it.getString(it.getColumnIndexOrThrow(COLUMN_GENRE))
                    )
                }
            }
        }
        return null
    }
}

class BookAdapter(
    private var books: MutableList<Book>,
    private val listener: (Book) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTV: TextView = itemView.findViewById(R.id.textViewItemTitle)
        private val authorTV: TextView = itemView.findViewById(R.id.textViewItemAuthor)

        fun bind(book: Book) {
            titleTV.text = book.title
            authorTV.text = book.author
            itemView.setOnClickListener { listener(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookViewHolder(v)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position])
    }

    override fun getItemCount() = books.size

    fun updateBooks(newBooks: List<Book>) {
        books.clear()
        books.addAll(newBooks)
        notifyDataSetChanged()
    }
}


class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: BookDbHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BookAdapter
    private lateinit var fabAdd: FloatingActionButton

    private lateinit var addBookLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewBookLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = BookDbHelper(this)
        recyclerView = findViewById(R.id.recyclerViewBooks)
        fabAdd = findViewById(R.id.fabAddBook)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BookAdapter(mutableListOf()) { book ->
            val intent = Intent(this, BookDetailsActivity::class.java).apply {
                putExtra("BOOK_ID", book.id)
            }
            viewBookLauncher.launch(intent)
        }
        recyclerView.adapter = adapter

        addBookLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) loadBooks()
        }

        viewBookLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            loadBooks()
        }

        fabAdd.setOnClickListener {
            val intent = Intent(this, AddBookActivity::class.java)
            addBookLauncher.launch(intent)
        }

        loadBooks()
    }

    private fun loadBooks() {
        val books = dbHelper.getAllBooks()
        adapter.updateBooks(books)
    }
}


class AddBookActivity : AppCompatActivity() {

    private lateinit var dbHelper: BookDbHelper
    private lateinit var titleET: EditText
    private lateinit var authorET: EditText
    private lateinit var genreET: EditText
    private lateinit var saveBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_book)

        dbHelper = BookDbHelper(this)
        titleET = findViewById(R.id.editTextBookTitle)
        authorET = findViewById(R.id.editTextBookAuthor)
        genreET = findViewById(R.id.editTextBookGenre)
        saveBtn = findViewById(R.id.buttonSaveBook)

        saveBtn.setOnClickListener {
            val title = titleET.text.toString().trim()
            val author = authorET.text.toString().trim()
            val genre = genreET.text.toString().trim()

            if (title.isEmpty() || author.isEmpty()) {
                Toast.makeText(this, "Please enter both title and author", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newBook = Book(title = title, author = author, genre = genre.ifEmpty { null })
            val id = dbHelper.addBook(newBook)
            if (id != -1L) {
                Toast.makeText(this, "Book saved", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Failed to save book", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


class BookDetailsActivity : AppCompatActivity() {

    private lateinit var dbHelper: BookDbHelper
    private lateinit var titleTV: TextView
    private lateinit var authorTV: TextView
    private lateinit var genreTV: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_details)

        titleTV = findViewById(R.id.textViewDetailTitle)
        authorTV = findViewById(R.id.textViewDetailAuthor)
        genreTV = findViewById(R.id.textViewDetailGenre)
        dbHelper = BookDbHelper(this)

        val bookId = intent.getLongExtra("BOOK_ID", -1)
        if (bookId == -1L) {
            Toast.makeText(this, "Invalid Book ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val book = dbHelper.getBook(bookId)
        if (book != null) {
            titleTV.text = book.title
            authorTV.text = book.author
            genreTV.text = book.genre ?: "N/A"
        } else {
            Toast.makeText(this, "Book not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
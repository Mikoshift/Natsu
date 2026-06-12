package io.mikoshift.natsu.data.book.import

sealed class BookImportException(message: String) : Exception(message)

class CannotOpenFileException :
    BookImportException("Cannot open file")

class EmptyFileException :
    BookImportException("File is empty")

class UnsupportedFormatException(fileName: String) :
    BookImportException("Unsupported file format: $fileName")

class FileTooLargeException :
    BookImportException("File exceeds maximum import size")

class UnsupportedEpubException :
    BookImportException("Unsupported EPUB")

class CorruptEpubException(cause: Throwable? = null) :
    BookImportException("Corrupt or unreadable EPUB") {
    init {
        cause?.let { initCause(it) }
    }
}

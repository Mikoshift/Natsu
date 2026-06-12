package io.mikoshift.natsu.data.book.import

sealed class BookImportException(message: String) : Exception(message)

class CannotOpenFileException :
    BookImportException("Cannot open file")

class EmptyFileException :
    BookImportException("File is empty")

class UnsupportedFormatException(fileName: String) :
    BookImportException("Unsupported file format: $fileName")

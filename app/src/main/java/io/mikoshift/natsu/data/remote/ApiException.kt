package io.mikoshift.natsu.data.remote

class ApiException(
    val code: Int,
    message: String,
    val errorBody: String? = null,
) : Exception(message)

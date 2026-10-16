package com.armanmaurya.internetradio.domain.model

import java.io.IOException

class MalformedBackupException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

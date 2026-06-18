package com.streamvault.domain.util

import java.io.IOException
import kotlinx.coroutines.CancellationException

fun Throwable.shouldRethrowDomainFlowFailure(): Boolean = when (this) {
    is CancellationException,
    is VirtualMachineError,
    is ThreadDeath,
    is LinkageError -> true
    is IOException -> false
    else -> true
}
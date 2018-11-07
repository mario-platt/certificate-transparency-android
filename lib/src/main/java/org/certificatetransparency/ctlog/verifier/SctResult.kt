package org.certificatetransparency.ctlog.verifier

import org.certificatetransparency.ctlog.internal.utils.stringStackTrace

sealed class SctResult {
    object Valid : SctResult()


    sealed class Invalid : SctResult() {
        object FailedVerification : Invalid()

        object NoVerifierFound : Invalid()

        data class FutureTimestamp(val timestamp: Long, val now: Long) : Invalid() {
            override fun toString() = "SCT timestamp, $timestamp, is in the future, current timestamp is $now."
        }

        data class LogServerUntrusted(val timestamp: Long, val logServerValidUntil: Long) : Invalid() {
            override fun toString() = "SCT timestamp, $timestamp, is greater than the log server validity, $logServerValidUntil."
        }

        abstract class Exception() : Invalid() {
            abstract val exception: kotlin.Exception?

            override fun toString() = "Unknown exception with ${exception?.stringStackTrace()}"
        }
    }
}
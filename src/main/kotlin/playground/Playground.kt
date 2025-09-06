package playground

import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

sealed class PaymentMethod {
    sealed class CreditCard : PaymentMethod() {
        data class Visa(val number: String) : CreditCard()
        data class MasterCard(val number: String) : CreditCard()
        data class Amex(val number: String) : CreditCard()
    }

    sealed class BankTransfer : PaymentMethod() {
        data class SEPA(val iban: String, val bic: String) : BankTransfer()
        data class SWIFT(val account: String, val swiftCode: String) : BankTransfer()
    }

    sealed class Crypto : PaymentMethod() {
        data class Bitcoin(val address: String) : Crypto()
        data class Ethereum(val address: String) : Crypto()
        data class Custom(val name: String, val address: String) : Crypto()
    }
}

fun describePayment(method: PaymentMethod): String = when (method) {
    is PaymentMethod.CreditCard.Visa       -> "Paid with Visa: ${method.number}"
    is PaymentMethod.CreditCard.MasterCard -> "Paid with MasterCard: ${method.number}"
    is PaymentMethod.CreditCard.Amex       -> "Paid with Amex: ${method.number}"

    is PaymentMethod.BankTransfer.SEPA     -> "Paid via SEPA. IBAN: ${method.iban}, BIC: ${method.bic}"
    is PaymentMethod.BankTransfer.SWIFT    -> "Paid via SWIFT. Account: ${method.account}, SWIFT: ${method.swiftCode}"

    is PaymentMethod.Crypto.Bitcoin        -> "Paid with Bitcoin. Address: ${method.address}"
    is PaymentMethod.Crypto.Ethereum       -> "Paid with Ethereum. Address: ${method.address}"
    is PaymentMethod.Crypto.Custom         -> "Paid with ${method.name}. Address: ${method.address}"
}

// An algebraic data type for HTTP responses
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class ClientError(val code: Int, val message: String) : ApiResult<Nothing>()
    data class ServerError(val code: Int, val message: String) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
    data object Unauthorized : ApiResult<Nothing>()
}

fun <T> handleApiResult(result: ApiResult<T>): String =
    when (result) {
        is ApiResult.Success -> "Got data: ${result.data}"
        is ApiResult.ClientError -> "Client error ${result.code}: ${result.message}"
        is ApiResult.ServerError -> "Server error ${result.code}: ${result.message}"
        ApiResult.NetworkError -> "No internet connection"
        ApiResult.Unauthorized -> "You must log in again"
    }

fun Int.squared(): Int = this * this
fun Long.squared(): Long = this * this

fun main() {
    println("Enter a number between 0 and 10:")
    val number = readln().toLongOrNull() ?: return println("Not a number")

    val (squares, time) = measureTimedValue {
        (0L..number).asSequence().map { it.squared() }.toList()
    }

    println("Computed squares up to $number in ${time.toInt(DurationUnit.MILLISECONDS)} ms")
    println("Largest square is ${squares.last()}")
}
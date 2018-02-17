package pl.dariuszseweryn.pingg.ipv4address

import android.widget.EditText
import com.jakewharton.rxbinding2.widget.textChanges
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import pl.dariuszseweryn.pingg.ipv4address.Ipv4AddressByteChange.*

fun EditText.ipv4AddressByteChanges(): Observable<Ipv4AddressByteChange>
        = this.textChanges()
        .map { it.toString() }
        .publish {
            Observable.zip(
                    it,
                    it.skip(1),
                    BiFunction { prvText: String, newText: String -> Pair(prvText, newText) }
            )
        }
        .flatMapMaybe { (previousText: String, newText: String) ->
            when {
                previousText.isAboveRange() || previousText.containsFractions() -> Maybe.empty()
                newText.isAboveRange() -> Maybe.just(AboveRange)
                newText.containsFractions() -> Maybe.just(Fractions(previousText))
                else -> Maybe.just(Valid)
            }
        }

sealed class Ipv4AddressByteChange {
    object Valid : Ipv4AddressByteChange()
    object AboveRange : Ipv4AddressByteChange()
    data class Fractions(val correctedText: String) : Ipv4AddressByteChange()
}

private fun String.containsFractions(): Boolean = this.contains(".") || this.contains(",")

private fun String.isAboveRange(): Boolean = this.toIntOrNull()?.let { it > 255 } ?: false
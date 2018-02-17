/*
 * Copyright 2017 Dariusz Seweryn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/dariuszseweryn/AndroidPing/blob/master/LICENSE.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.dariuszseweryn.pingg

import android.content.Context
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers.io
import pl.dariuszseweryn.pingg.core.PingResult
import pl.dariuszseweryn.pingg.core.singlePing
import pl.dariuszseweryn.pingg.ipv4address.Ipv4AddressByteChange
import pl.dariuszseweryn.pingg.ipv4address.ipv4AddressByteChanges
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val pingTargetTextView: TextView by lazy { findViewById<TextView>(R.id.pingTargetTv) }
    private val pingResultTextView: TextView by lazy { findViewById<TextView>(R.id.pingResultTv) }

    private val saveButton: Button by lazy { findViewById<Button>(R.id.saveButton) }

    private val address0EditText: EditText by lazy { findViewById<EditText>(R.id.address0) }
    private val address1EditText: EditText by lazy { findViewById<EditText>(R.id.address1) }
    private val address2EditText: EditText by lazy { findViewById<EditText>(R.id.address2) }
    private val address3EditText: EditText by lazy { findViewById<EditText>(R.id.address3) }

    private var lifetimeDisposable = CompositeDisposable()
    private var runningDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arrayOf(address0EditText, address1EditText, address2EditText, address3EditText)
                .forEach { editText ->
                    editText.ipv4AddressByteChanges()
                            .subscribe {
                                when(it) {
                                    Ipv4AddressByteChange.Valid -> editText.error = null
                                    Ipv4AddressByteChange.AboveRange -> {
                                        editText.error = "Must be 0-255"
                                        editText.setTextWithCursorOnEnd("255")
                                    }
                                    is Ipv4AddressByteChange.Fractions -> {
                                        editText.error = "Must not have fractions"
                                        editText.setTextWithCursorOnEnd(it.correctedText)
                                    }
                                }
                            }
                            .also { lifetimeDisposable.add(it) }
                }
    }

    override fun onResume() {
        super.onResume()

        loadPingAddress()
                .subscribeOn(AndroidSchedulers.mainThread())
                .repeatWhen { it.flatMap {
                    saveButton.clicks()
                            .flatMapMaybe { saveMaybe() }
                            .take(1)
                            .toFlowable(BackpressureStrategy.MISSING)
                } }
                .toObservable()
                .switchMap {

                    val singlePingObservable = singlePing(it, 5L, TimeUnit.SECONDS)
                            .map { PingResult.Success(it) as PingResult }
                            .onErrorReturnItem(PingResult.Unreachable)
                            .toObservable()

                    Observable.interval(0L, 5L, TimeUnit.SECONDS, io())
                            .concatMap { singlePingObservable }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is PingResult.Success -> {
                            val result = "%.2f ms".format(it.pingInNanos * 0.000001)
                            pingResultTextView.text = result
                            Log.i("Ping", result)
                        }
                        PingResult.Unreachable -> {
                            val message = "Unreachable"
                            pingResultTextView.text = message
                            Log.w("Ping", message)
                        }
                    }
                }
                .also { runningDisposable.add(it) }
    }

    override fun onPause() {
        super.onPause()
        runningDisposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifetimeDisposable.clear()
    }

    private fun getSavedAddress() = getSharedPreferences("addresses", Context.MODE_PRIVATE)
            .getString("address", null)

    private fun saveMaybe(): Maybe<Unit> = Maybe.fromCallable {
        val byte0 = address0EditText.text.toString().toIntOrNull()
        val byte1 = address1EditText.text.toString().toIntOrNull()
        val byte2 = address2EditText.text.toString().toIntOrNull()
        val byte3 = address3EditText.text.toString().toIntOrNull()
        if (byte0 == null || byte1 == null || byte2 == null || byte3 == null) {
            return@fromCallable null
        }
        val newAddress = "%d.%d.%d.%d".format(byte0, byte1, byte2, byte3)

        if (newAddress == getSavedAddress()) return@fromCallable Unit

        getSharedPreferences("addresses", Context.MODE_PRIVATE)
                .edit()
                .putString("address", newAddress)
                .apply()
    }

    private fun loadPingAddress(): Maybe<InetAddress> = Maybe.fromCallable {
        getSavedAddress()
                ?.let { address ->
                    val byteArray = ByteArray(4)
                    val intAddress = address
                            .split('.')
                            .map { it.toInt() }
                            .also {
                                it.forEachIndexed { index: Int, addressByteAsInt: Int ->
                                    byteArray[index] = addressByteAsInt.toUnsignedByte()
                                }
                            }

                    address0EditText.setText(intAddress[0].toString())
                    address1EditText.setText(intAddress[1].toString())
                    address2EditText.setText(intAddress[2].toString())
                    address3EditText.setText(intAddress[3].toString())

                    pingTargetTextView.text = address

                    InetAddress.getByAddress(byteArray)
                }
    }

    private fun Int.toUnsignedByte() = (this and 0xFF).toByte()

    private fun EditText.setTextWithCursorOnEnd(text: String)
            = this.setText(text).also { this.setSelection(text.length) }

}

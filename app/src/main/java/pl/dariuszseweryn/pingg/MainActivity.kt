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
import android.support.annotation.IdRes
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers.io
import pl.dariuszseweryn.pingg.core.PingResult
import pl.dariuszseweryn.pingg.core.singlePing
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val byte0 = getAddressAsInt(R.id.address0)
            val byte1 = getAddressAsInt(R.id.address1)
            val byte2 = getAddressAsInt(R.id.address2)
            val byte3 = getAddressAsInt(R.id.address3)
            if (byte0 == null || byte1 == null || byte2 == null || byte3 == null) {
                return@setOnClickListener
            }
            getSharedPreferences("addresses", Context.MODE_PRIVATE)
                    .edit()
                    .putString("address", "%d.%d.%d.%d".format(byte0, byte1, byte2, byte3))
                    .apply()
        }
    }

    override fun onResume() {
        super.onResume()
        val textView = findViewById<TextView>(R.id.pingResultTextView)

        val inetAddress = getSharedPreferences("addresses", Context.MODE_PRIVATE)
                .getString("address", null)
                ?.let { address ->
                    val byteArray = ByteArray(4)
                    address.split('.')
                            .map { (it.toInt() and 0xFF).toByte() }
                            .forEachIndexed { index: Int, byte: Byte -> byteArray[index] = byte }
                    InetAddress.getByAddress(byteArray)
                } ?: return

        val singlePingObservable = singlePing(
                inetAddress,
                5L, TimeUnit.SECONDS
        )
                .map { PingResult.Success(it) as PingResult }
                .onErrorReturnItem(PingResult.Unreachable)
                .toObservable()

        disposable = Observable.interval(0L, 5L, TimeUnit.SECONDS, io())
                .concatMap { singlePingObservable }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is PingResult.Success -> {
                            val result = "%.2f ms".format(it.pingInNanos * 0.000001)
                            textView.text = result
                            Log.i("Ping", result)
                        }
                        PingResult.Unreachable -> {
                            val message = "Unreachable"
                            textView.text = message
                            Log.w("Ping", message)
                        }
                    }
                }
    }

    override fun onPause() {
        super.onPause()

        disposable?.dispose()
        disposable = null
    }

//    private fun setAddressConstraint(@IdRes editTextResId: Int) {
//        val editText = findViewById<EditText>(editTextResId)
//        editText.addTextChangedListener(object : TextWatcher {
//            override fun afterTextChanged(p0: Editable?) {
//                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//            }
//
//            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
//                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//            }
//
//            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
//                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//            }
//
//        })
//    }

    private fun getAddressAsInt(@IdRes editTextResId: Int): Int?
            = (findViewById<EditText>(editTextResId)).text.toString().toIntOrNull()

}

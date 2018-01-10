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

package pl.dariuszseweryn.pingg.core

import io.reactivex.Single
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

fun singlePing(
        inetAddress: InetAddress,
        timeout: Long,
        timeoutTimeUnit: TimeUnit
): Single<Long> = Single.create { emitter ->
    var datagramSocket: DatagramSocket? = null
    try {
        datagramSocket = DatagramSocket()
        datagramSocket.soTimeout = timeoutTimeUnit.toMillis(timeout).toInt()

        val sendByteArray = ByteArray(8)
        val sendByteBuffer = ByteBuffer.wrap(sendByteArray)
        val sendDatagramPacket = DatagramPacket(sendByteArray, 8, inetAddress, 7)

        val receiveByteArray = ByteArray(8)
        val receiveDatagramPacket = DatagramPacket(receiveByteArray, receiveByteArray.size)

        sendByteBuffer.putLong(System.nanoTime())
        datagramSocket.send(sendDatagramPacket)
        datagramSocket.receive(receiveDatagramPacket)

        val end = System.nanoTime()
        val start = ByteBuffer.wrap(receiveByteArray).long

        if (!emitter.isDisposed) emitter.onSuccess(end - start)
    } catch (e: Throwable) {
        if (!emitter.isDisposed) emitter.onError(e)
    } finally {
        datagramSocket?.close()
    }
}

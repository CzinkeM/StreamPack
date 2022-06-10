/*
 * Copyright (C) 2022 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.thibaultbee.streampack.internal.muxers.mp4.boxes

import io.github.thibaultbee.streampack.internal.utils.extensions.putInt
import java.nio.ByteBuffer

class TrackFragmentBaseMediaDecodeTimeBox(private val baseMediaDecodeTime: Long, version: Byte) :
    FullBox("tfdt", version, 0) {
    override val size: Int = super.size + if (version == 1.toByte()) {
        8
    } else {
        4
    }

    override fun write(output: ByteBuffer) {
        super.write(output)
        when (version) {
            1.toByte() -> output.putLong(baseMediaDecodeTime)
            0.toByte() -> output.putInt(baseMediaDecodeTime)
            else -> throw IllegalArgumentException("version must be 0 or 1")
        }
    }
}
package com.cheku.app.carmode

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/** Shizuku UserService 的 Binder 契约 */
interface ISpectrumService {
    /** 建立捕获通路并开始出帧 */
    fun start(): String

    /** 停止捕获并释放 AudioRecord / AudioPolicy */
    fun stop()

    /** 是否正在捕获 */
    fun isCapturing(): Boolean

    fun describe(): String

    /** 最新一帧的 96 个频段 */
    fun latestBands(): FloatArray?

    /** 累计推帧数 */
    fun frameCount(): Long

    companion object {
        const val DESCRIPTOR = "com.cheku.app.carmode.ISpectrumService"

        /** Shizuku 约定 */
        const val TX_DESTROY = 16777114

        const val TX_START = 1
        const val TX_STOP = 2
        const val TX_IS_CAPTURING = 3
        const val TX_DESCRIBE = 4
        const val TX_LATEST_BANDS = 5
        const val TX_FRAME_COUNT = 6

        /** 把远端 Binder 包成可直接调用的接口 */
        fun asInterface(binder: IBinder?): ISpectrumService? =
            binder?.let { Proxy(it) }
    }

    abstract class Stub : Binder(), ISpectrumService {
        init {
            attachInterface(null, DESCRIPTOR)
        }

        /** 由子类实现 */
        abstract fun destroy()

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == TX_DESTROY) {
                destroy()
                return true
            }
            when (code) {
                TX_START -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = start()
                    reply?.apply {
                        writeNoException()
                        writeString(result)
                    }
                    return true
                }
                TX_STOP -> {
                    data.enforceInterface(DESCRIPTOR)
                    stop()
                    reply?.writeNoException()
                    return true
                }
                TX_IS_CAPTURING -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = isCapturing()
                    reply?.apply {
                        writeNoException()
                        writeInt(if (result) 1 else 0)
                    }
                    return true
                }
                TX_DESCRIBE -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = describe()
                    reply?.apply {
                        writeNoException()
                        writeString(result)
                    }
                    return true
                }
                TX_LATEST_BANDS -> {
                    data.enforceInterface(DESCRIPTOR)
                    val frame = latestBands()
                    reply?.apply {
                        writeNoException()
                        // 用长度 -1 表示 null：`writeFloatArray` 不接受 null，
                        // 而 Parcel 的 writeTypedArray 之类对 float[] 不合适。
                        if (frame == null) {
                            writeInt(-1)
                        } else {
                            writeInt(frame.size)
                            writeFloatArray(frame)
                        }
                    }
                    return true
                }
                TX_FRAME_COUNT -> {
                    data.enforceInterface(DESCRIPTOR)
                    val count = frameCount()
                    reply?.apply {
                        writeNoException()
                        writeLong(count)
                    }
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private class Proxy(private val remote: IBinder) : ISpectrumService {
        override fun start(): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_START, data, reply, 0)
                reply.readException()
                return reply.readString().orEmpty()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun stop() {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_STOP, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun isCapturing(): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_IS_CAPTURING, data, reply, 0)
                reply.readException()
                return reply.readInt() != 0
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun describe(): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_DESCRIBE, data, reply, 0)
                reply.readException()
                return reply.readString().orEmpty()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun latestBands(): FloatArray? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_LATEST_BANDS, data, reply, 0)
                reply.readException()
                val size = reply.readInt()
                if (size < 0) return null
                val out = FloatArray(size)
                reply.readFloatArray(out)
                return out
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun frameCount(): Long {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TX_FRAME_COUNT, data, reply, 0)
                reply.readException()
                return reply.readLong()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }
}

package com.lucent.app.local

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface ILocalLlmEngine : IInterface {
    @Throws(RemoteException::class)
    fun ensureLoaded(gpuEnabled: Boolean): Boolean

    @Throws(RemoteException::class)
    fun supportsVision(): Boolean

    @Throws(RemoteException::class)
    fun generate(roles: Array<String>?, texts: Array<String>?, imagePaths: Array<String>?, callback: ILocalLlmCallback?)

    @Throws(RemoteException::class)
    fun stop()

    @Throws(RemoteException::class)
    fun shutdown()

    abstract class Stub : Binder(), ILocalLlmEngine {
        companion object {
            private const val DESCRIPTOR = "com.lucent.app.local.ILocalLlmEngine"
            private const val TRANSACTION_ensureLoaded = IBinder.FIRST_CALL_TRANSACTION + 0
            private const val TRANSACTION_supportsVision = IBinder.FIRST_CALL_TRANSACTION + 1
            private const val TRANSACTION_generate = IBinder.FIRST_CALL_TRANSACTION + 2
            private const val TRANSACTION_stop = IBinder.FIRST_CALL_TRANSACTION + 3
            private const val TRANSACTION_shutdown = IBinder.FIRST_CALL_TRANSACTION + 4

            fun asInterface(obj: IBinder?): ILocalLlmEngine? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is ILocalLlmEngine) return iin
                return Proxy(obj)
            }
        }

        init {
            this.attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_ensureLoaded -> {
                    data.enforceInterface(DESCRIPTOR)
                    val gpuEnabled = data.readInt() != 0
                    val result = this.ensureLoaded(gpuEnabled)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                TRANSACTION_supportsVision -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = this.supportsVision()
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                TRANSACTION_generate -> {
                    data.enforceInterface(DESCRIPTOR)
                    val roles = data.createStringArray()
                    val texts = data.createStringArray()
                    val imagePaths = data.createStringArray()
                    val callback = ILocalLlmCallback.Stub.asInterface(data.readStrongBinder())
                    this.generate(roles, texts, imagePaths, callback)
                    return true
                }
                TRANSACTION_stop -> {
                    data.enforceInterface(DESCRIPTOR)
                    this.stop()
                    return true
                }
                TRANSACTION_shutdown -> {
                    data.enforceInterface(DESCRIPTOR)
                    this.shutdown()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(private val mRemote: IBinder) : ILocalLlmEngine {
            override fun asBinder(): IBinder = mRemote

            @Throws(RemoteException::class)
            override fun ensureLoaded(gpuEnabled: Boolean): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(if (gpuEnabled) 1 else 0)
                    mRemote.transact(TRANSACTION_ensureLoaded, data, reply, 0)
                    reply.readException()
                    return reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun supportsVision(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote.transact(TRANSACTION_supportsVision, data, reply, 0)
                    reply.readException()
                    return reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun generate(roles: Array<String>?, texts: Array<String>?, imagePaths: Array<String>?, callback: ILocalLlmCallback?) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeStringArray(roles)
                    data.writeStringArray(texts)
                    data.writeStringArray(imagePaths)
                    data.writeStrongBinder(callback?.asBinder())
                    mRemote.transact(TRANSACTION_generate, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun stop() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote.transact(TRANSACTION_stop, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun shutdown() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    mRemote.transact(TRANSACTION_shutdown, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }
    }
}

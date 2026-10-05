package com.lucent.app.local

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException

interface ILocalLlmCallback : IInterface {
    @Throws(RemoteException::class)
    fun onPiece(piece: String?)

    @Throws(RemoteException::class)
    fun onDone(rc: Int)

    open class Stub : Binder(), ILocalLlmCallback {
        @Throws(RemoteException::class)
        override fun onPiece(piece: String?) {}

        @Throws(RemoteException::class)
        override fun onDone(rc: Int) {}
        companion object {
            private const val DESCRIPTOR = "com.lucent.app.local.ILocalLlmCallback"
            private const val TRANSACTION_onPiece = IBinder.FIRST_CALL_TRANSACTION + 0
            private const val TRANSACTION_onDone = IBinder.FIRST_CALL_TRANSACTION + 1

            fun asInterface(obj: IBinder?): ILocalLlmCallback? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is ILocalLlmCallback) return iin
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
                TRANSACTION_onPiece -> {
                    data.enforceInterface(DESCRIPTOR)
                    val piece = data.readString()
                    this.onPiece(piece)
                    return true
                }
                TRANSACTION_onDone -> {
                    data.enforceInterface(DESCRIPTOR)
                    val rc = data.readInt()
                    this.onDone(rc)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(private val mRemote: IBinder) : ILocalLlmCallback {
            override fun asBinder(): IBinder = mRemote

            @Throws(RemoteException::class)
            override fun onPiece(piece: String?) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(piece)
                    mRemote.transact(TRANSACTION_onPiece, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun onDone(rc: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(rc)
                    mRemote.transact(TRANSACTION_onDone, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }
    }
}

package com.lucent.app.local;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ILocalLlmCallback extends IInterface {
    void onPiece(String piece) throws RemoteException;
    void onDone(int rc) throws RemoteException;

    abstract class Stub extends Binder implements ILocalLlmCallback {
        private static final String DESCRIPTOR = "com.lucent.app.local.ILocalLlmCallback";
        static final int TRANSACTION_onPiece = IBinder.FIRST_CALL_TRANSACTION + 0;
        static final int TRANSACTION_onDone = IBinder.FIRST_CALL_TRANSACTION + 1;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static ILocalLlmCallback asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof ILocalLlmCallback) return (ILocalLlmCallback) iin;
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_onPiece: {
                    data.enforceInterface(DESCRIPTOR);
                    String piece = data.readString();
                    this.onPiece(piece);
                    return true;
                }
                case TRANSACTION_onDone: {
                    data.enforceInterface(DESCRIPTOR);
                    int rc = data.readInt();
                    this.onDone(rc);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements ILocalLlmCallback {
            private IBinder mRemote;
            Proxy(IBinder remote) { mRemote = remote; }

            @Override
            public IBinder asBinder() { return mRemote; }

            public String getInterfaceDescriptor() { return DESCRIPTOR; }

            @Override
            public void onPiece(String piece) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(piece);
                    mRemote.transact(TRANSACTION_onPiece, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onDone(int rc) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(rc);
                    mRemote.transact(TRANSACTION_onDone, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        }
    }
}

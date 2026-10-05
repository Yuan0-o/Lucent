package com.lucent.app.local;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ILocalLlmEngine extends IInterface {
    boolean ensureLoaded(boolean gpuEnabled) throws RemoteException;
    boolean supportsVision() throws RemoteException;
    void generate(String[] roles, String[] texts, String[] imagePaths, ILocalLlmCallback callback) throws RemoteException;
    void stop() throws RemoteException;
    void shutdown() throws RemoteException;

    abstract class Stub extends Binder implements ILocalLlmEngine {
        private static final String DESCRIPTOR = "com.lucent.app.local.ILocalLlmEngine";
        static final int TRANSACTION_ensureLoaded = IBinder.FIRST_CALL_TRANSACTION + 0;
        static final int TRANSACTION_supportsVision = IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_generate = IBinder.FIRST_CALL_TRANSACTION + 2;
        static final int TRANSACTION_stop = IBinder.FIRST_CALL_TRANSACTION + 3;
        static final int TRANSACTION_shutdown = IBinder.FIRST_CALL_TRANSACTION + 4;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static ILocalLlmEngine asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof ILocalLlmEngine) return (ILocalLlmEngine) iin;
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
                case TRANSACTION_ensureLoaded: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean gpuEnabled = data.readInt() != 0;
                    boolean result = this.ensureLoaded(gpuEnabled);
                    reply.writeNoException();
                    reply.writeInt(result ? 1 : 0);
                    return true;
                }
                case TRANSACTION_supportsVision: {
                    data.enforceInterface(DESCRIPTOR);
                    boolean result = this.supportsVision();
                    reply.writeNoException();
                    reply.writeInt(result ? 1 : 0);
                    return true;
                }
                case TRANSACTION_generate: {
                    data.enforceInterface(DESCRIPTOR);
                    String[] roles = data.createStringArray();
                    String[] texts = data.createStringArray();
                    String[] imagePaths = data.createStringArray();
                    ILocalLlmCallback callback = ILocalLlmCallback.Stub.asInterface(data.readStrongBinder());
                    this.generate(roles, texts, imagePaths, callback);
                    return true;
                }
                case TRANSACTION_stop: {
                    data.enforceInterface(DESCRIPTOR);
                    this.stop();
                    return true;
                }
                case TRANSACTION_shutdown: {
                    data.enforceInterface(DESCRIPTOR);
                    this.shutdown();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements ILocalLlmEngine {
            private IBinder mRemote;
            Proxy(IBinder remote) { mRemote = remote; }

            @Override
            public IBinder asBinder() { return mRemote; }

            public String getInterfaceDescriptor() { return DESCRIPTOR; }

            @Override
            public boolean ensureLoaded(boolean gpuEnabled) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(gpuEnabled ? 1 : 0);
                    mRemote.transact(TRANSACTION_ensureLoaded, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public boolean supportsVision() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_supportsVision, data, reply, 0);
                    reply.readException();
                    return reply.readInt() != 0;
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void generate(String[] roles, String[] texts, String[] imagePaths, ILocalLlmCallback callback) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStringArray(roles);
                    data.writeStringArray(texts);
                    data.writeStringArray(imagePaths);
                    data.writeStrongBinder(callback != null ? callback.asBinder() : null);
                    mRemote.transact(TRANSACTION_generate, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void stop() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_stop, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void shutdown() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_shutdown, data, null, IBinder.FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        }
    }
}

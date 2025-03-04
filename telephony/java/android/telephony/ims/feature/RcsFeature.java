/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.telephony.ims.feature;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.CapabilityExchangeAidlWrapper;
import android.telephony.ims.aidl.ICapabilityExchangeEventListener;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IOptionsResponseCallback;
import android.telephony.ims.aidl.IPublishResponseCallback;
import android.telephony.ims.aidl.ISubscribeResponseCallback;
import android.telephony.ims.aidl.RcsOptionsResponseAidlWrapper;
import android.telephony.ims.aidl.RcsPublishResponseAidlWrapper;
import android.telephony.ims.aidl.RcsSubscribeResponseAidlWrapper;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.OptionsResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.PublishResponseCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.SubscribeResponseCallback;
import android.util.Log;

import com.android.internal.telephony.util.TelephonyUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Base implementation of the RcsFeature APIs. Any ImsService wishing to support RCS should extend
 * this class and provide implementations of the RcsFeature methods that they support.
 * @hide
 */
@SystemApi
public class RcsFeature extends ImsFeature {

    private static final String LOG_TAG = "RcsFeature";

    private static final class RcsFeatureBinder extends IImsRcsFeature.Stub {
        // Reference the outer class in order to have better test coverage metrics instead of
        // creating a inner class referencing the outer class directly.
        private final RcsFeature mReference;
        private final Executor mExecutor;

        RcsFeatureBinder(RcsFeature classRef, @CallbackExecutor Executor executor) {
            mReference = classRef;
            mExecutor = executor;
        }

        @Override
        public int queryCapabilityStatus() throws RemoteException {
            return executeMethodAsyncForResult(
                    () -> mReference.queryCapabilityStatus().mCapabilities,
                    "queryCapabilityStatus");
        }

        @Override
        public void addCapabilityCallback(IImsCapabilityCallback c) throws RemoteException {
            executeMethodAsync(() -> mReference.addCapabilityCallback(c), "addCapabilityCallback");
        }

        @Override
        public void removeCapabilityCallback(IImsCapabilityCallback c) throws RemoteException {
            executeMethodAsync(() -> mReference.removeCapabilityCallback(c),
                    "removeCapabilityCallback");
        }

        @Override
        public void changeCapabilitiesConfiguration(CapabilityChangeRequest r,
                IImsCapabilityCallback c) throws RemoteException {
            executeMethodAsync(() -> mReference.requestChangeEnabledCapabilities(r, c),
                    "changeCapabilitiesConfiguration");
        }

        @Override
        public void queryCapabilityConfiguration(int capability, int radioTech,
                IImsCapabilityCallback c) throws RemoteException {
            executeMethodAsync(() -> mReference.queryCapabilityConfigurationInternal(capability,
                    radioTech, c), "queryCapabilityConfiguration");
        }

        @Override
        public int getFeatureState() throws RemoteException {
            return executeMethodAsyncForResult(mReference::getFeatureState, "getFeatureState");
        }

        // RcsCapabilityExchangeImplBase specific APIs
        @Override
        public void setCapabilityExchangeEventListener(
                @Nullable ICapabilityExchangeEventListener listener) throws RemoteException {
            CapabilityExchangeEventListener listenerWrapper =
                    new CapabilityExchangeAidlWrapper(listener);
            executeMethodAsync(() -> mReference.setCapabilityExchangeEventListener(
                    mExecutor, listenerWrapper), "setCapabilityExchangeEventListener");
        }

        @Override
        public void publishCapabilities(@NonNull String pidfXml,
                @NonNull IPublishResponseCallback callback) throws RemoteException {
            PublishResponseCallback callbackWrapper = new RcsPublishResponseAidlWrapper(callback);
            executeMethodAsync(() -> mReference.getCapabilityExchangeImplBaseInternal()
                    .publishCapabilities(pidfXml, callbackWrapper), "publishCapabilities");
        }

        @Override
        public void subscribeForCapabilities(@NonNull List<Uri> uris,
                @NonNull ISubscribeResponseCallback callback) throws RemoteException {
            SubscribeResponseCallback wrapper = new RcsSubscribeResponseAidlWrapper(callback);
            executeMethodAsync(() -> mReference.getCapabilityExchangeImplBaseInternal()
                    .subscribeForCapabilities(uris, wrapper), "subscribeForCapabilities");
        }

        @Override
        public void sendOptionsCapabilityRequest(@NonNull Uri contactUri,
                @NonNull List<String> myCapabilities, @NonNull IOptionsResponseCallback callback)
                throws RemoteException {
            OptionsResponseCallback callbackWrapper = new RcsOptionsResponseAidlWrapper(callback);
            executeMethodAsync(() -> mReference.getCapabilityExchangeImplBaseInternal()
                    .sendOptionsCapabilityRequest(contactUri, myCapabilities, callbackWrapper),
                    "sendOptionsCapabilityRequest");
        }

        // Call the methods with a clean calling identity on the executor and wait indefinitely for
        // the future to return.
        private void executeMethodAsync(Runnable r, String errorLogName)
                throws RemoteException {
            // call with a clean calling identity on the executor and wait indefinitely for the
            // future to return.
            try {
                CompletableFuture.runAsync(
                        () -> TelephonyUtils.runWithCleanCallingIdentity(r), mExecutor).join();
            } catch (CancellationException | CompletionException e) {
                Log.w(LOG_TAG, "RcsFeatureBinder - " + errorLogName + " exception: "
                        + e.getMessage());
                throw new RemoteException(e.getMessage());
            }
        }

        private <T> T executeMethodAsyncForResult(Supplier<T> r,
                String errorLogName) throws RemoteException {
            // call with a clean calling identity on the executor and wait indefinitely for the
            // future to return.
            CompletableFuture<T> future = CompletableFuture.supplyAsync(
                    () -> TelephonyUtils.runWithCleanCallingIdentity(r), mExecutor);
            try {
                return future.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.w(LOG_TAG, "RcsFeatureBinder - " + errorLogName + " exception: "
                        + e.getMessage());
                throw new RemoteException(e.getMessage());
            }
        }
    }

    /**
     * Contains the capabilities defined and supported by a {@link RcsFeature} in the
     * form of a bitmask. The capabilities that are used in the RcsFeature are
     * defined as:
     * {@link RcsUceAdatper.RcsImsCapabilityFlag#CAPABILITY_TYPE_OPTIONS_UCE}
     * {@link RceUceAdapter.RcsImsCapabilityFlag#CAPABILITY_TYPE_PRESENCE_UCE}
     *
     * The enabled capabilities of this RcsFeature will be set by the framework
     * using {@link #changeEnabledCapabilities(CapabilityChangeRequest, CapabilityCallbackProxy)}.
     * After the capabilities have been set, the RcsFeature may then perform the necessary bring up
     * of the capability and notify the capability status as true using
     * {@link #notifyCapabilitiesStatusChanged(RcsImsCapabilities)}. This will signal to the
     * framework that the capability is available for usage.
     * @hide
     */
    public static class RcsImsCapabilities extends Capabilities {
        /** @hide*/
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = "CAPABILITY_TYPE_", flag = true, value = {
                CAPABILITY_TYPE_NONE,
                CAPABILITY_TYPE_OPTIONS_UCE,
                CAPABILITY_TYPE_PRESENCE_UCE
        })
        public @interface RcsImsCapabilityFlag {}

        /**
         * Undefined capability type for initialization
         */
        public static final int CAPABILITY_TYPE_NONE = 0;

        /**
         * This carrier supports User Capability Exchange using SIP OPTIONS as defined by the
         * framework. If set, the RcsFeature should support capability exchange using SIP OPTIONS.
         * If not set, this RcsFeature should not service capability requests.
         */
        public static final int CAPABILITY_TYPE_OPTIONS_UCE = 1 << 0;

        /**
         * This carrier supports User Capability Exchange using a presence server as defined by the
         * framework. If set, the RcsFeature should support capability exchange using a presence
         * server. If not set, this RcsFeature should not publish capabilities or service capability
         * requests using presence.
         */
        public static final int CAPABILITY_TYPE_PRESENCE_UCE =  1 << 1;

        public RcsImsCapabilities(@RcsUceAdapter.RcsImsCapabilityFlag int capabilities) {
            super(capabilities);
        }

        private RcsImsCapabilities(Capabilities c) {
            super(c.getMask());
        }

        @Override
        public void addCapabilities(@RcsUceAdapter.RcsImsCapabilityFlag int capabilities) {
            super.addCapabilities(capabilities);
        }

        @Override
        public void removeCapabilities(@RcsUceAdapter.RcsImsCapabilityFlag int capabilities) {
            super.removeCapabilities(capabilities);
        }

        @Override
        public boolean isCapable(@RcsUceAdapter.RcsImsCapabilityFlag int capabilities) {
            return super.isCapable(capabilities);
        }
    }

    private final Executor mExecutor;
    private final RcsFeatureBinder mImsRcsBinder;
    private RcsCapabilityExchangeImplBase mCapabilityExchangeImpl;
    private CapabilityExchangeEventListener mCapExchangeEventListener;

    /**
     * Create a new RcsFeature.
     * <p>
     * Method stubs called from the framework will be called asynchronously. To specify the
     * {@link Executor} that the methods stubs will be called, use
     * {@link RcsFeature#RcsFeature(Executor)} instead.
     *
     * @deprecated Use {@link #RcsFeature(Executor)} to create the RcsFeature.
     */
    @Deprecated
    public RcsFeature() {
        super();
        mExecutor = Runnable::run;
        // Run on the Binder threads that call them.
        mImsRcsBinder = new RcsFeatureBinder(this, mExecutor);
    }

    /**
     * Create a new RcsFeature using the Executor specified for methods being called by the
     * framework.
     * @param executor The executor for the framework to use when executing the methods overridden
     * by the implementation of RcsFeature.
     */
    public RcsFeature(@NonNull Executor executor) {
        super();
        if (executor == null) {
            throw new IllegalArgumentException("executor can not be null.");
        }
        mExecutor = executor;
        // Run on the Binder thread by default.
        mImsRcsBinder = new RcsFeatureBinder(this, mExecutor);
    }

    /**
     * Called when the RcsFeature is initialized.
     *
     * @param context The context that is used in the ImsService.
     * @param slotId The slot ID associated with the RcsFeature.
     * @hide
     */
    @Override
    public void initialize(Context context, int slotId) {
        super.initialize(context, slotId);
        // Notify that the RcsFeature is ready.
        mExecutor.execute(() -> onFeatureReady());
    }

    /**
     * Query the current {@link RcsImsCapabilities} status set by the RcsFeature. If a capability is
     * set, the {@link RcsFeature} has brought up the capability and is ready for framework
     * requests. To change the status of the capabilities
     * {@link #notifyCapabilitiesStatusChanged(RcsImsCapabilities)} should be called.
     * @hide
     */
    @Override
    public @NonNull final RcsImsCapabilities queryCapabilityStatus() {
        return new RcsImsCapabilities(super.queryCapabilityStatus());
    }

    /**
     * Notify the framework that the capabilities status has changed. If a capability is enabled,
     * this signals to the framework that the capability has been initialized and is ready.
     * Call {@link #queryCapabilityStatus()} to return the current capability status.
     * @hide
     */
    public final void notifyCapabilitiesStatusChanged(@NonNull RcsImsCapabilities c) {
        if (c == null) {
            throw new IllegalArgumentException("RcsImsCapabilities must be non-null!");
        }
        super.notifyCapabilitiesStatusChanged(c);
    }

    /**
     * Provides the RcsFeature with the ability to return the framework capability configuration set
     * by the framework. When the framework calls
     * {@link #changeEnabledCapabilities(CapabilityChangeRequest, CapabilityCallbackProxy)} to
     * enable or disable capability A, this method should return the correct configuration for
     * capability A afterwards (until it has changed).
     * @hide
     */
    public boolean queryCapabilityConfiguration(
            @RcsUceAdapter.RcsImsCapabilityFlag int capability,
            @ImsRegistrationImplBase.ImsRegistrationTech int radioTech) {
        // Base Implementation - Override to provide functionality
        return false;
    }
    /**
     * Called from the framework when the {@link RcsImsCapabilities} that have been configured for
     * this {@link RcsFeature} has changed.
     * <p>
     * For each newly enabled capability flag, the corresponding capability should be brought up in
     * the {@link RcsFeature} and registered on the network. For each newly disabled capability
     * flag, the corresponding capability should be brought down, and deregistered. Once a new
     * capability has been initialized and is ready for usage, the status of that capability should
     * also be set to true using {@link #notifyCapabilitiesStatusChanged(RcsImsCapabilities)}. This
     * will notify the framework that the capability is ready.
     * <p>
     * If for some reason one or more of these capabilities can not be enabled/disabled,
     * {@link CapabilityCallbackProxy#onChangeCapabilityConfigurationError(int, int, int)} should
     * be called for each capability change that resulted in an error.
     * @hide
     */
    @Override
    public void changeEnabledCapabilities(@NonNull CapabilityChangeRequest request,
            @NonNull CapabilityCallbackProxy c) {
        // Base Implementation - Override to provide functionality
    }

    /**
     * Retrieve the implementation of UCE for this {@link RcsFeature}, which can use either
     * presence or OPTIONS for capability exchange.
     *
     * Will only be requested by the framework if capability exchange is configured
     * as capable during a
     * {@link #changeEnabledCapabilities(CapabilityChangeRequest, CapabilityCallbackProxy)}
     * operation and the RcsFeature sets the status of the capability to true using
     * {@link #notifyCapabilitiesStatusChanged(RcsImsCapabilities)}.
     *
     * @param executor The executor for the framework to use when request RCS resquests to this
     * service.
     * @param listener A {@link CapabilityExchangeEventListener} to send the capability exchange
     * event to the framework.
     * @return An instance of {@link RcsCapabilityExchangeImplBase} that implements capability
     * exchange if it is supported by the device.
     */
    public @NonNull RcsCapabilityExchangeImplBase createCapabilityExchangeImpl(
            @NonNull Executor executor, @NonNull CapabilityExchangeEventListener listener) {
        // Base Implementation, override to implement functionality
        return new RcsCapabilityExchangeImplBase(executor);
    }

    /**
     * Remove the given CapabilityExchangeImplBase instance.
     * @param capExchangeImpl The {@link RcsCapabilityExchangeImplBase} instance to be removed.
     */
    public void removeCapabilityExchangeImpl(
            @NonNull RcsCapabilityExchangeImplBase capExchangeImpl) {
        // Override to implement the process of removing RcsCapabilityExchangeImplBase instance.
    }

    /**{@inheritDoc}*/
    @Override
    public void onFeatureRemoved() {

    }

    /**{@inheritDoc}*/
    @Override
    public void onFeatureReady() {

    }

    /**
     * @hide
     */
    @Override
    public final IImsRcsFeature getBinder() {
        return mImsRcsBinder;
    }

    /**
     * Set the capability exchange listener.
     * @param executor The executor for the framework to use when request RCS requests to this
     * service.
     * @param listener A {@link CapabilityExchangeEventListener} to send the capability exchange
     * event to the framework.
     */
    private void setCapabilityExchangeEventListener(@NonNull Executor executor,
            @Nullable CapabilityExchangeEventListener listener) {
        synchronized (mLock) {
            mCapExchangeEventListener = listener;
            if (mCapExchangeEventListener != null) {
                initRcsCapabilityExchangeImplBase(executor, mCapExchangeEventListener);
            } else {
                // Remove the RcsCapabilityExchangeImplBase instance when the capability exchange
                // instance has been removed in the framework.
                if (mCapabilityExchangeImpl != null) {
                    removeCapabilityExchangeImpl(mCapabilityExchangeImpl);
                }
                mCapabilityExchangeImpl = null;
            }
        }
    }

    /**
     * Initialize the RcsCapabilityExchangeImplBase instance if the capability exchange instance
     * has already been created in the framework.
     * @param executor The executor for the framework to use when request RCS requests to this
     * service.
     * @param listener A {@link CapabilityExchangeEventListener} to send the capability exchange
     * event to the framework.
     */
    private void initRcsCapabilityExchangeImplBase(@NonNull Executor executor,
            @NonNull CapabilityExchangeEventListener listener) {
        synchronized (mLock) {
            // Remove the original instance
            if (mCapabilityExchangeImpl != null) {
                removeCapabilityExchangeImpl(mCapabilityExchangeImpl);
            }
            mCapabilityExchangeImpl = createCapabilityExchangeImpl(executor, listener);
        }
    }

    /**
     * @return the {@link RcsCapabilityExchangeImplBase} associated with the RcsFeature.
     */
    private @NonNull RcsCapabilityExchangeImplBase getCapabilityExchangeImplBaseInternal() {
        synchronized (mLock) {
            // The method should not be called if the instance of RcsCapabilityExchangeImplBase has
            // not been created yet.
            if (mCapabilityExchangeImpl == null) {
                throw new IllegalStateException("Session is not available.");
            }
            return mCapabilityExchangeImpl;
        }
    }
}

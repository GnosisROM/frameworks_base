/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License.
 */
package android.telephony;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.Annotation.CallState;
import android.telephony.Annotation.DataActivityType;
import android.telephony.Annotation.DisconnectCauses;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.PreciseCallStates;
import android.telephony.Annotation.PreciseDisconnectCauses;
import android.telephony.Annotation.RadioPowerState;
import android.telephony.Annotation.SimActivationState;
import android.telephony.Annotation.SrvccState;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.telephony.IOnSubscriptionsChangedListener;
import com.android.internal.telephony.ITelephonyRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A centralized place to notify telephony related status changes, e.g, {@link ServiceState} update
 * or {@link PhoneCapability} changed. This might trigger callback from applications side through
 * {@link android.telephony.PhoneStateListener}
 *
 * TODO: limit API access to only carrier apps with certain permissions or apps running on
 * privileged UID.
 *
 * @hide
 */
public class TelephonyRegistryManager {

    private static final String TAG = "TelephonyRegistryManager";
    private static ITelephonyRegistry sRegistry;
    private final Context mContext;

    /**
     * A mapping between {@link SubscriptionManager.OnSubscriptionsChangedListener} and
     * its callback IOnSubscriptionsChangedListener.
     */
    private final Map<SubscriptionManager.OnSubscriptionsChangedListener,
                IOnSubscriptionsChangedListener> mSubscriptionChangedListenerMap = new HashMap<>();
    /**
     * A mapping between {@link SubscriptionManager.OnOpportunisticSubscriptionsChangedListener} and
     * its callback IOnSubscriptionsChangedListener.
     */
    private final Map<SubscriptionManager.OnOpportunisticSubscriptionsChangedListener,
            IOnSubscriptionsChangedListener> mOpportunisticSubscriptionChangedListenerMap
            = new HashMap<>();


    /** @hide **/
    public TelephonyRegistryManager(@NonNull Context context) {
        mContext = context;
        if (sRegistry == null) {
            sRegistry = ITelephonyRegistry.Stub.asInterface(
                ServiceManager.getService("telephony.registry"));
        }
    }

    /**
     * Register for changes to the list of active {@link SubscriptionInfo} records or to the
     * individual records themselves. When a change occurs the onSubscriptionsChanged method of
     * the listener will be invoked immediately if there has been a notification. The
     * onSubscriptionChanged method will also be triggered once initially when calling this
     * function.
     *
     * @param listener an instance of {@link SubscriptionManager.OnSubscriptionsChangedListener}
     *                 with onSubscriptionsChanged overridden.
     * @param executor the executor that will execute callbacks.
     */
    public void addOnSubscriptionsChangedListener(
            @NonNull SubscriptionManager.OnSubscriptionsChangedListener listener,
            @NonNull Executor executor) {
        if (mSubscriptionChangedListenerMap.get(listener) != null) {
            Log.d(TAG, "addOnSubscriptionsChangedListener listener already present");
            return;
        }
        IOnSubscriptionsChangedListener callback = new IOnSubscriptionsChangedListener.Stub() {
            @Override
            public void onSubscriptionsChanged () {
                executor.execute(() -> listener.onSubscriptionsChanged());
            }
        };
        mSubscriptionChangedListenerMap.put(listener, callback);
        try {
            sRegistry.addOnSubscriptionsChangedListener(mContext.getOpPackageName(),
                    mContext.getAttributionTag(), callback);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Unregister the {@link SubscriptionManager.OnSubscriptionsChangedListener}. This is not
     * strictly necessary as the listener will automatically be unregistered if an attempt to
     * invoke the listener fails.
     *
     * @param listener that is to be unregistered.
     */
    public void removeOnSubscriptionsChangedListener(
            @NonNull SubscriptionManager.OnSubscriptionsChangedListener listener) {
        if (mSubscriptionChangedListenerMap.get(listener) == null) {
            return;
        }
        try {
            sRegistry.removeOnSubscriptionsChangedListener(mContext.getOpPackageName(),
                    mSubscriptionChangedListenerMap.get(listener));
            mSubscriptionChangedListenerMap.remove(listener);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Register for changes to the list of opportunistic subscription records or to the
     * individual records themselves. When a change occurs the onOpportunisticSubscriptionsChanged
     * method of the listener will be invoked immediately if there has been a notification.
     *
     * @param listener an instance of
     * {@link SubscriptionManager.OnOpportunisticSubscriptionsChangedListener} with
     *                 onOpportunisticSubscriptionsChanged overridden.
     * @param executor an Executor that will execute callbacks.
     */
    public void addOnOpportunisticSubscriptionsChangedListener(
            @NonNull SubscriptionManager.OnOpportunisticSubscriptionsChangedListener listener,
            @NonNull Executor executor) {
        if (mOpportunisticSubscriptionChangedListenerMap.get(listener) != null) {
            Log.d(TAG, "addOnOpportunisticSubscriptionsChangedListener listener already present");
            return;
        }
        /**
         * The callback methods need to be called on the executor thread where
         * this object was created.  If the binder did that for us it'd be nice.
         */
        IOnSubscriptionsChangedListener callback = new IOnSubscriptionsChangedListener.Stub() {
            @Override
            public void onSubscriptionsChanged() {
                final long identity = Binder.clearCallingIdentity();
                try {
                    Log.d(TAG, "onOpportunisticSubscriptionsChanged callback received.");
                    executor.execute(() -> listener.onOpportunisticSubscriptionsChanged());
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        };
        mOpportunisticSubscriptionChangedListenerMap.put(listener, callback);
        try {
            sRegistry.addOnOpportunisticSubscriptionsChangedListener(mContext.getOpPackageName(),
                    mContext.getAttributionTag(), callback);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Unregister the {@link SubscriptionManager.OnOpportunisticSubscriptionsChangedListener}
     * that is currently listening opportunistic subscriptions change. This is not strictly
     * necessary as the listener will automatically be unregistered if an attempt to invoke the
     * listener fails.
     *
     * @param listener that is to be unregistered.
     */
    public void removeOnOpportunisticSubscriptionsChangedListener(
            @NonNull SubscriptionManager.OnOpportunisticSubscriptionsChangedListener listener) {
        if (mOpportunisticSubscriptionChangedListenerMap.get(listener) == null) {
            return;
        }
        try {
            sRegistry.removeOnSubscriptionsChangedListener(mContext.getOpPackageName(),
                    mOpportunisticSubscriptionChangedListenerMap.get(listener));
            mOpportunisticSubscriptionChangedListenerMap.remove(listener);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * To check the SDK version for {@link #listenWithEventList}.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.P)
    private static final long LISTEN_CODE_CHANGE = 147600208L;

    /**
     * Listen for incoming subscriptions
     * @param subId Subscription ID
     * @param pkg Package name
     * @param featureId Feature ID
     * @param listener Listener providing callback
     * @param events List events
     * @param notifyNow Whether to notify instantly
     */
    public void listenWithEventList(int subId, @NonNull String pkg, @NonNull String featureId,
            @NonNull PhoneStateListener listener, @NonNull int[] events, boolean notifyNow) {
        try {
            // subId from PhoneStateListener is deprecated Q on forward, use the subId from
            // TelephonyManager instance. Keep using subId from PhoneStateListener for pre-Q.
            if (Compatibility.isChangeEnabled(LISTEN_CODE_CHANGE)) {
                // Since mSubId in PhoneStateListener is deprecated from Q on forward, this is
                // the only place to set mSubId and its for "informational" only.
                listener.mSubId = (events.length == 0)
                        ? SubscriptionManager.INVALID_SUBSCRIPTION_ID : subId;
            } else if (listener.mSubId != null) {
                subId = listener.mSubId;
            }
            sRegistry.listenWithEventList(
                    subId, pkg, featureId, listener.callback, events, notifyNow);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Informs the system of an intentional upcoming carrier network change by a carrier app.
     * This call only used to allow the system to provide alternative UI while telephony is
     * performing an action that may result in intentional, temporary network lack of connectivity.
     * <p>
     * Based on the active parameter passed in, this method will either show or hide the alternative
     * UI. There is no timeout associated with showing this UX, so a carrier app must be sure to
     * call with active set to false sometime after calling with it set to {@code true}.
     * <p>
     * Requires Permission: calling app has carrier privileges.
     *
     * @param active Whether the carrier network change is or shortly will be
     * active. Set this value to true to begin showing alternative UI and false to stop.
     * @see TelephonyManager#hasCarrierPrivileges
     */
    public void notifyCarrierNetworkChange(boolean active) {
        try {
            sRegistry.notifyCarrierNetworkChange(active);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify call state changed on certain subscription.
     *
     * @param subId for which call state changed.
     * @param slotIndex for which call state changed. Can be derived from subId except when subId is
     * invalid.
     * @param state latest call state. e.g, offhook, ringing
     * @param incomingNumber incoming phone number.
     */
    public void notifyCallStateChanged(int subId, int slotIndex, @CallState int state,
            @Nullable String incomingNumber) {
        try {
            sRegistry.notifyCallState(slotIndex, subId, state, incomingNumber);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify call state changed on all subscriptions.
     *
     * @param state latest call state. e.g, offhook, ringing
     * @param incomingNumber incoming phone number.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    public void notifyCallStateChangedForAllSubscriptions(@CallState int state,
            @Nullable String incomingNumber) {
        try {
            sRegistry.notifyCallStateForAllSubs(state, incomingNumber);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify {@link SubscriptionInfo} change.
     * @hide
     */
    public void notifySubscriptionInfoChanged() {
        try {
            sRegistry.notifySubscriptionInfoChanged();
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify opportunistic {@link SubscriptionInfo} change.
     * @hide
     */
    public void notifyOpportunisticSubscriptionInfoChanged() {
        try {
            sRegistry.notifyOpportunisticSubscriptionInfoChanged();
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify {@link ServiceState} update on certain subscription.
     *
     * @param subId for which the service state changed.
     * @param slotIndex for which the service state changed. Can be derived from subId except
     * subId is invalid.
     * @param state service state e.g, in service, out of service or roaming status.
     */
    public void notifyServiceStateChanged(int subId, int slotIndex, @NonNull ServiceState state) {
        try {
            sRegistry.notifyServiceStateForPhoneId(slotIndex, subId, state);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify {@link SignalStrength} update on certain subscription.
     *
     * @param subId for which the signalstrength changed.
     * @param slotIndex for which the signalstrength changed. Can be derived from subId except when
     * subId is invalid.
     * @param signalStrength e.g, signalstrength level {@see SignalStrength#getLevel()}
     */
    public void notifySignalStrengthChanged(int subId, int slotIndex,
            @NonNull SignalStrength signalStrength) {
        try {
            sRegistry.notifySignalStrengthForPhoneId(slotIndex, subId, signalStrength);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify changes to the message-waiting indicator on certain subscription. e.g, The status bar
     * uses message waiting indicator to determine when to display the voicemail icon.
     *
     * @param subId for which message waiting indicator changed.
     * @param slotIndex for which message waiting indicator changed. Can be derived from subId
     * except when subId is invalid.
     * @param msgWaitingInd {@code true} indicates there is message-waiting indicator, {@code false}
     * otherwise.
     */
    public void notifyMessageWaitingChanged(int subId, int slotIndex, boolean msgWaitingInd) {
        try {
            sRegistry.notifyMessageWaitingChangedForPhoneId(slotIndex, subId, msgWaitingInd);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify changes to the call-forwarding status on certain subscription.
     *
     * @param subId for which call forwarding status changed.
     * @param callForwardInd {@code true} indicates there is call forwarding, {@code false}
     * otherwise.
     */
    public void notifyCallForwardingChanged(int subId, boolean callForwardInd) {
        try {
            sRegistry.notifyCallForwardingChangedForSubscriber(subId, callForwardInd);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify changes to activity state changes on certain subscription.
     *
     * @param subId for which data activity state changed.
     * @param dataActivityType indicates the latest data activity type e.g, {@link
     * TelephonyManager#DATA_ACTIVITY_IN}
     */
    public void notifyDataActivityChanged(int subId, @DataActivityType int dataActivityType) {
        try {
            sRegistry.notifyDataActivityForSubscriber(subId, dataActivityType);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify changes to default (Internet) data connection state on certain subscription.
     *
     * @param subId for which data connection state changed.
     * @param slotIndex for which data connections state changed. Can be derived from subId except
     * when subId is invalid.
     * @param preciseState the PreciseDataConnectionState
     *
     * @see PreciseDataConnectionState
     * @see TelephonyManager#DATA_DISCONNECTED
     */
    public void notifyDataConnectionForSubscriber(int slotIndex, int subId,
            @NonNull PreciseDataConnectionState preciseState) {
        try {
            sRegistry.notifyDataConnectionForSubscriber(
                    slotIndex, subId, preciseState);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify {@link CallQuality} change on certain subscription.
     *
     * @param subId for which call quality state changed.
     * @param slotIndex for which call quality state changed. Can be derived from subId except when
     * subId is invalid.
     * @param callQuality Information about call quality e.g, call quality level
     * @param networkType associated with this data connection. e.g, LTE
     */
    public void notifyCallQualityChanged(int subId, int slotIndex, @NonNull CallQuality callQuality,
        @NetworkType int networkType) {
        try {
            sRegistry.notifyCallQualityChanged(callQuality, slotIndex, subId, networkType);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify emergency number list changed on certain subscription.
     *
     * @param subId for which emergency number list changed.
     * @param slotIndex for which emergency number list changed. Can be derived from subId except
     * when subId is invalid.
     */
    public void notifyEmergencyNumberList(int subId, int slotIndex) {
        try {
            sRegistry.notifyEmergencyNumberList(slotIndex, subId);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify outgoing emergency call.
     * @param phoneId Sender phone ID.
     * @param subId Sender subscription ID.
     * @param emergencyNumber Emergency number.
     */
    public void notifyOutgoingEmergencyCall(int phoneId, int subId,
            @NonNull EmergencyNumber emergencyNumber) {
        try {
            sRegistry.notifyOutgoingEmergencyCall(phoneId, subId, emergencyNumber);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify outgoing emergency SMS.
     * @param phoneId Sender phone ID.
     * @param subId Sender subscription ID.
     * @param emergencyNumber Emergency number.
     */
    public void notifyOutgoingEmergencySms(int phoneId, int subId,
            @NonNull EmergencyNumber emergencyNumber) {
        try {
            sRegistry.notifyOutgoingEmergencySms(phoneId, subId, emergencyNumber);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify radio power state changed on certain subscription.
     *
     * @param subId for which radio power state changed.
     * @param slotIndex for which radio power state changed. Can be derived from subId except when
     * subId is invalid.
     * @param radioPowerState the current modem radio state.
     */
    public void notifyRadioPowerStateChanged(int subId, int slotIndex,
        @RadioPowerState int radioPowerState) {
        try {
            sRegistry.notifyRadioPowerStateChanged(slotIndex, subId, radioPowerState);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify {@link PhoneCapability} changed.
     *
     * @param phoneCapability the capability of the modem group.
     */
    public void notifyPhoneCapabilityChanged(@NonNull PhoneCapability phoneCapability) {
        try {
            sRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Sim activation type: voice
     * @see #notifyVoiceActivationStateChanged
     * @hide
     */
    public static final int SIM_ACTIVATION_TYPE_VOICE = 0;
    /**
     * Sim activation type: data
     * @see #notifyDataActivationStateChanged
     * @hide
     */
    public static final int SIM_ACTIVATION_TYPE_DATA = 1;

    /**
     * Notify data activation state changed on certain subscription.
     * @see TelephonyManager#getDataActivationState()
     *
     * @param subId for which data activation state changed.
     * @param slotIndex for which data activation state changed. Can be derived from subId except
     * when subId is invalid.
     * @param activationState sim activation state e.g, activated.
     */
    public void notifyDataActivationStateChanged(int subId, int slotIndex,
        @SimActivationState int activationState) {
        try {
            sRegistry.notifySimActivationStateChangedForPhoneId(slotIndex, subId,
                    SIM_ACTIVATION_TYPE_DATA, activationState);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify voice activation state changed on certain subscription.
     * @see TelephonyManager#getVoiceActivationState()
     *
     * @param subId for which voice activation state changed.
     * @param slotIndex for which voice activation state changed. Can be derived from subId except
     * subId is invalid.
     * @param activationState sim activation state e.g, activated.
     */
    public void notifyVoiceActivationStateChanged(int subId, int slotIndex,
        @SimActivationState int activationState) {
        try {
            sRegistry.notifySimActivationStateChangedForPhoneId(slotIndex, subId,
                    SIM_ACTIVATION_TYPE_VOICE, activationState);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify User mobile data state changed on certain subscription. e.g, mobile data is enabled
     * or disabled.
     *
     * @param subId for which mobile data state has changed.
     * @param slotIndex for which mobile data state has changed. Can be derived from subId except
     * when subId is invalid.
     * @param state {@code true} indicates mobile data is enabled/on. {@code false} otherwise.
     */
    public void notifyUserMobileDataStateChanged(int slotIndex, int subId, boolean state) {
        try {
            sRegistry.notifyUserMobileDataStateChangedForPhoneId(slotIndex, subId, state);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify display info changed.
     *
     * @param slotIndex The SIM slot index for which display info has changed. Can be
     * derived from {@code subscriptionId} except when {@code subscriptionId} is invalid, such as
     * when the device is in emergency-only mode.
     * @param subscriptionId Subscription id for which display network info has changed.
     * @param telephonyDisplayInfo The display info.
     */
    public void notifyDisplayInfoChanged(int slotIndex, int subscriptionId,
                                         @NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
        try {
            sRegistry.notifyDisplayInfoChanged(slotIndex, subscriptionId, telephonyDisplayInfo);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify IMS call disconnect causes which contains {@link android.telephony.ims.ImsReasonInfo}.
     *
     * @param subId for which ims call disconnect.
     * @param imsReasonInfo the reason for ims call disconnect.
     */
    public void notifyImsDisconnectCause(int subId, @NonNull ImsReasonInfo imsReasonInfo) {
        try {
            sRegistry.notifyImsDisconnectCause(subId, imsReasonInfo);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify single Radio Voice Call Continuity (SRVCC) state change for the currently active call
     * on certain subscription.
     *
     * @param subId for which srvcc state changed.
     * @param state srvcc state
     */
    public void notifySrvccStateChanged(int subId, @SrvccState int state) {
        try {
            sRegistry.notifySrvccStateChanged(subId, state);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify precise call state changed on certain subscription, including foreground, background
     * and ringcall states.
     *
     * @param subId for which precise call state changed.
     * @param slotIndex for which precise call state changed. Can be derived from subId except when
     * subId is invalid.
     * @param ringCallPreciseState ringCall state.
     * @param foregroundCallPreciseState foreground call state.
     * @param backgroundCallPreciseState background call state.
     */
    public void notifyPreciseCallState(int subId, int slotIndex,
            @PreciseCallStates int ringCallPreciseState,
            @PreciseCallStates int foregroundCallPreciseState,
            @PreciseCallStates int backgroundCallPreciseState) {
        try {
            sRegistry.notifyPreciseCallState(slotIndex, subId, ringCallPreciseState,
                foregroundCallPreciseState, backgroundCallPreciseState);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify call disconnect causes which contains {@link DisconnectCause} and {@link
     * android.telephony.PreciseDisconnectCause}.
     *
     * @param slotIndex for which call disconnected. Can be derived from subId except when subId is
     * invalid.
     * @param subId for which call disconnected.
     * @param cause {@link DisconnectCause} for the disconnected call.
     * @param preciseCause {@link android.telephony.PreciseDisconnectCause} for the disconnected
     * call.
     */
    public void notifyDisconnectCause(int slotIndex, int subId, @DisconnectCauses int cause,
            @PreciseDisconnectCauses int preciseCause) {
        try {
            sRegistry.notifyDisconnectCause(slotIndex, subId, cause, preciseCause);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify {@link android.telephony.CellLocation} changed.
     *
     * <p>To be compatible with {@link TelephonyRegistry}, use {@link CellIdentity} which is
     * parcelable, and convert to CellLocation in client code.
     */
    public void notifyCellLocation(int subId, @NonNull CellIdentity cellLocation) {
        try {
            sRegistry.notifyCellLocationForSubscriber(subId, cellLocation);
        } catch (RemoteException ex) {
            // system process is dead
        }
    }

    /**
     * Notify {@link CellInfo} changed on certain subscription. e.g, when an observed cell info has
     * changed or new cells have been added or removed on the given subscription.
     *
     * @param subId for which cellinfo changed.
     * @param cellInfo A list of cellInfo associated with the given subscription.
     */
    public void notifyCellInfoChanged(int subId, @NonNull List<CellInfo> cellInfo) {
        try {
            sRegistry.notifyCellInfoForSubscriber(subId, cellInfo);
        } catch (RemoteException ex) {

        }
    }

    /**
     * Notify that the active data subscription ID has changed.
     * @param activeDataSubId The new subscription ID for active data
     */
    public void notifyActiveDataSubIdChanged(int activeDataSubId) {
        try {
            sRegistry.notifyActiveDataSubIdChanged(activeDataSubId);
        } catch (RemoteException ex) {

        }
    }

    /**
     * Report that Registration or a Location/Routing/Tracking Area update has failed.
     *
     * @param slotIndex for which call disconnected. Can be derived from subId except when subId is
     * invalid.
     * @param subId for which cellinfo changed.
     * @param cellIdentity the CellIdentity, which must include the globally unique identifier
     *        for the cell (for example, all components of the CGI or ECGI).
     * @param chosenPlmn a 5 or 6 digit alphanumeric PLMN (MCC|MNC) among those broadcast by the
     *         cell that was chosen for the failed registration attempt.
     * @param domain DOMAIN_CS, DOMAIN_PS or both in case of a combined procedure.
     * @param causeCode the primary failure cause code of the procedure.
     *        For GSM/UMTS (MM), values are in TS 24.008 Sec 10.5.95
     *        For GSM/UMTS (GMM), values are in TS 24.008 Sec 10.5.147
     *        For LTE (EMM), cause codes are TS 24.301 Sec 9.9.3.9
     *        For NR (5GMM), cause codes are TS 24.501 Sec 9.11.3.2
     *        Integer.MAX_VALUE if this value is unused.
     * @param additionalCauseCode the cause code of any secondary/combined procedure if appropriate.
     *        For UMTS, if a combined attach succeeds for PS only, then the GMM cause code shall be
     *        included as an additionalCauseCode. For LTE (ESM), cause codes are in
     *        TS 24.301 9.9.4.4. Integer.MAX_VALUE if this value is unused.
     */
    public void notifyRegistrationFailed(int slotIndex, int subId,
            @NonNull CellIdentity cellIdentity, @NonNull String chosenPlmn,
            int domain, int causeCode, int additionalCauseCode) {
        try {
            sRegistry.notifyRegistrationFailed(slotIndex, subId, cellIdentity,
                    chosenPlmn, domain, causeCode, additionalCauseCode);
        } catch (RemoteException ex) {
        }
    }

    /**
     * Notify {@link BarringInfo} has changed for a specific subscription.
     *
     * @param slotIndex for the phone object that got updated barring info.
     * @param subId for which the BarringInfo changed.
     * @param barringInfo updated BarringInfo.
     */
    public void notifyBarringInfoChanged(
            int slotIndex, int subId, @NonNull BarringInfo barringInfo) {
        try {
            sRegistry.notifyBarringInfoChanged(slotIndex, subId, barringInfo);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify {@link PhysicalChannelConfig} has changed for a specific subscription.
     *
     * @param subId the subId
     * @param configs a list of {@link PhysicalChannelConfig}, the configs of physical channel.
     */
    public void notifyPhysicalChannelConfigForSubscriber(
            int subId, List<PhysicalChannelConfig> configs) {
        try {
            sRegistry.notifyPhysicalChannelConfigForSubscriber(subId, configs);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    /**
     * Notify that the data enabled has changed.
     *
     * @param enabled True if data is enabled, otherwise disabled.
     * @param reason Reason for data enabled/disabled. See {@code REASON_*} in
     * {@link TelephonyManager}.
     */
    public void notifyDataEnabled(boolean enabled, @TelephonyManager.DataEnabledReason int reason) {
        try {
            sRegistry.notifyDataEnabled(enabled, reason);
        } catch (RemoteException ex) {
            // system server crash
        }
    }

    public @NonNull Set<Integer> getEventsFromListener(@NonNull PhoneStateListener listener) {

        Set<Integer> eventList = new ArraySet<>();

        if (listener instanceof PhoneStateListener.ServiceStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_SERVICE_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.MessageWaitingIndicatorChangedListener) {
            eventList.add(PhoneStateListener.EVENT_MESSAGE_WAITING_INDICATOR_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CallForwardingIndicatorChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CALL_FORWARDING_INDICATOR_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CellLocationChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CELL_LOCATION_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CallStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CALL_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.DataConnectionStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_DATA_CONNECTION_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.DataActivityListener) {
            eventList.add(PhoneStateListener.EVENT_DATA_ACTIVITY_CHANGED);
        }

        if (listener instanceof PhoneStateListener.SignalStrengthsChangedListener) {
            eventList.add(PhoneStateListener.EVENT_SIGNAL_STRENGTHS_CHANGED);
        }

        if (listener instanceof PhoneStateListener.AlwaysReportedSignalStrengthChangedListener) {
            eventList.add(PhoneStateListener.EVENT_ALWAYS_REPORTED_SIGNAL_STRENGTH_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CellInfoChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CELL_INFO_CHANGED);
        }

        if (listener instanceof PhoneStateListener.PreciseCallStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_PRECISE_CALL_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CallDisconnectCauseChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CALL_DISCONNECT_CAUSE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.ImsCallDisconnectCauseChangedListener) {
            eventList.add(PhoneStateListener.EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.PreciseDataConnectionStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.SrvccStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_SRVCC_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.VoiceActivationStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_VOICE_ACTIVATION_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.DataActivationStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_DATA_ACTIVATION_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.UserMobileDataStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_USER_MOBILE_DATA_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.DisplayInfoChangedListener) {
            eventList.add(PhoneStateListener.EVENT_DISPLAY_INFO_CHANGED);
        }

        if (listener instanceof PhoneStateListener.EmergencyNumberListChangedListener) {
            eventList.add(PhoneStateListener.EVENT_EMERGENCY_NUMBER_LIST_CHANGED);
        }

        if (listener instanceof PhoneStateListener.OutgoingEmergencyCallListener) {
            eventList.add(PhoneStateListener.EVENT_OUTGOING_EMERGENCY_CALL);
        }

        if (listener instanceof PhoneStateListener.OutgoingEmergencySmsListener) {
            eventList.add(PhoneStateListener.EVENT_OUTGOING_EMERGENCY_SMS);
        }

        if (listener instanceof PhoneStateListener.PhoneCapabilityChangedListener) {
            eventList.add(PhoneStateListener.EVENT_PHONE_CAPABILITY_CHANGED);
        }

        if (listener instanceof PhoneStateListener.ActiveDataSubscriptionIdChangedListener) {
            eventList.add(PhoneStateListener.EVENT_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGED);
        }

        if (listener instanceof PhoneStateListener.RadioPowerStateChangedListener) {
            eventList.add(PhoneStateListener.EVENT_RADIO_POWER_STATE_CHANGED);
        }

        if (listener instanceof PhoneStateListener.CarrierNetworkChangeListener) {
            eventList.add(PhoneStateListener.EVENT_CARRIER_NETWORK_CHANGED);
        }

        if (listener instanceof PhoneStateListener.RegistrationFailedListener) {
            eventList.add(PhoneStateListener.EVENT_REGISTRATION_FAILURE);
        }

        if (listener instanceof PhoneStateListener.CallAttributesChangedListener) {
            eventList.add(PhoneStateListener.EVENT_CALL_ATTRIBUTES_CHANGED);
        }

        if (listener instanceof PhoneStateListener.BarringInfoChangedListener) {
            eventList.add(PhoneStateListener.EVENT_BARRING_INFO_CHANGED);
        }

        if (listener instanceof PhoneStateListener.PhysicalChannelConfigChangedListener) {
            eventList.add(PhoneStateListener.EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        }

        if (listener instanceof PhoneStateListener.DataEnabledChangedListener) {
            eventList.add(PhoneStateListener.EVENT_DATA_ENABLED_CHANGED);
        }

        return eventList;
    }

    private @NonNull Set<Integer> getEventsFromBitmask(int eventMask) {

        Set<Integer> eventList = new ArraySet<>();

        if ((eventMask & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_SERVICE_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
            eventList.add(PhoneStateListener.EVENT_SIGNAL_STRENGTH_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
            eventList.add(PhoneStateListener.EVENT_MESSAGE_WAITING_INDICATOR_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
            eventList.add(PhoneStateListener.EVENT_CALL_FORWARDING_INDICATOR_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
            eventList.add(PhoneStateListener.EVENT_CELL_LOCATION_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_CALL_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_DATA_CONNECTION_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
            eventList.add(PhoneStateListener.EVENT_DATA_ACTIVITY_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
            eventList.add(PhoneStateListener.EVENT_SIGNAL_STRENGTHS_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_ALWAYS_REPORTED_SIGNAL_STRENGTH) != 0) {
            eventList.add(PhoneStateListener.EVENT_ALWAYS_REPORTED_SIGNAL_STRENGTH_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CELL_INFO) != 0) {
            eventList.add(PhoneStateListener.EVENT_CELL_INFO_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_PRECISE_CALL_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_PRECISE_CALL_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_DATA_CONNECTION_REAL_TIME_INFO) != 0) {
            eventList.add(PhoneStateListener.EVENT_DATA_CONNECTION_REAL_TIME_INFO_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_OEM_HOOK_RAW_EVENT) != 0) {
            eventList.add(PhoneStateListener.EVENT_OEM_HOOK_RAW);
        }

        if ((eventMask & PhoneStateListener.LISTEN_SRVCC_STATE_CHANGED) != 0) {
            eventList.add(PhoneStateListener.EVENT_SRVCC_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CARRIER_NETWORK_CHANGE) != 0) {
            eventList.add(PhoneStateListener.EVENT_CARRIER_NETWORK_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_VOICE_ACTIVATION_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_VOICE_ACTIVATION_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_DATA_ACTIVATION_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_DATA_ACTIVATION_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE) != 0) {
            eventList.add(PhoneStateListener.EVENT_USER_MOBILE_DATA_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED) != 0) {
            eventList.add(PhoneStateListener.EVENT_DISPLAY_INFO_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_PHONE_CAPABILITY_CHANGE) != 0) {
            eventList.add(PhoneStateListener.EVENT_PHONE_CAPABILITY_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE) != 0) {
            eventList.add(PhoneStateListener.EVENT_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED) != 0) {
            eventList.add(PhoneStateListener.EVENT_RADIO_POWER_STATE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_EMERGENCY_NUMBER_LIST) != 0) {
            eventList.add(PhoneStateListener.EVENT_EMERGENCY_NUMBER_LIST_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CALL_DISCONNECT_CAUSES) != 0) {
            eventList.add(PhoneStateListener.EVENT_CALL_DISCONNECT_CAUSE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_CALL_ATTRIBUTES_CHANGED) != 0) {
            eventList.add(PhoneStateListener.EVENT_CALL_ATTRIBUTES_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_IMS_CALL_DISCONNECT_CAUSES) != 0) {
            eventList.add(PhoneStateListener.EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED);
        }

        if ((eventMask & PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_CALL) != 0) {
            eventList.add(PhoneStateListener.EVENT_OUTGOING_EMERGENCY_CALL);
        }

        if ((eventMask & PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_SMS) != 0) {
            eventList.add(PhoneStateListener.EVENT_OUTGOING_EMERGENCY_SMS);
        }

        if ((eventMask & PhoneStateListener.LISTEN_REGISTRATION_FAILURE) != 0) {
            eventList.add(PhoneStateListener.EVENT_REGISTRATION_FAILURE);
        }

        if ((eventMask & PhoneStateListener.LISTEN_BARRING_INFO) != 0) {
            eventList.add(PhoneStateListener.EVENT_BARRING_INFO_CHANGED);
        }
        return eventList;

    }

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     * <p>
     * To register a listener, pass a {@link PhoneStateListener} which implements
     * interfaces of events. For example,
     * FakeServiceStateChangedListener extends {@link PhoneStateListener} implements
     * {@link PhoneStateListener.ServiceStateChangedListener}.
     *
     * At registration, and when a specified telephony state changes, the telephony manager invokes
     * the appropriate callback method on the listener object and passes the current (updated)
     * values.
     * <p>
     *
     * If this TelephonyManager object has been created with
     * {@link TelephonyManager#createForSubscriptionId}, applies to the given subId.
     * Otherwise, applies to {@link SubscriptionManager#getDefaultSubscriptionId()}.
     * To listen events for multiple subIds, pass a separate listener object to
     * each TelephonyManager object created with {@link TelephonyManager#createForSubscriptionId}.
     *
     * Note: if you call this method while in the middle of a binder transaction, you <b>must</b>
     * call {@link android.os.Binder#clearCallingIdentity()} before calling this method. A
     * {@link SecurityException} will be thrown otherwise.
     *
     * This API should be used sparingly -- large numbers of listeners will cause system
     * instability. If a process has registered too many listeners without unregistering them, it
     * may encounter an {@link IllegalStateException} when trying to register more listeners.
     *
     * @param listener The {@link PhoneStateListener} object to register.
     */
    public void registerPhoneStateListener(@NonNull @CallbackExecutor Executor executor, int subId,
            String pkgName, String attributionTag, @NonNull PhoneStateListener listener,
            boolean notifyNow) {
        listener.setExecutor(executor);
        registerPhoneStateListener(subId, pkgName, attributionTag, listener,
                getEventsFromListener(listener), notifyNow);
    }

    public void registerPhoneStateListenerWithEvents(int subId, String pkgName,
            String attributionTag, @NonNull PhoneStateListener listener, int events,
            boolean notifyNow) {
        registerPhoneStateListener(
                subId, pkgName, attributionTag, listener, getEventsFromBitmask(events), notifyNow);
    }

    private void registerPhoneStateListener(int subId,
            String pkgName, String attributionTag, @NonNull PhoneStateListener listener,
            @NonNull Set<Integer> events, boolean notifyNow) {
        if (listener == null) {
            throw new IllegalStateException("telephony service is null.");
        }

        listenWithEventList(subId, pkgName, attributionTag, listener,
                events.stream().mapToInt(i -> i).toArray(), notifyNow);
    }

    /**
     * Unregister an existing {@link PhoneStateListener}.
     *
     * @param listener The {@link PhoneStateListener} object to unregister.
     */
    public void unregisterPhoneStateListener(int subId, String pkgName, String attributionTag,
                                             @NonNull PhoneStateListener listener,
                                             boolean notifyNow) {
        listenWithEventList(subId, pkgName, attributionTag, listener, new int[0], notifyNow);
    }
}

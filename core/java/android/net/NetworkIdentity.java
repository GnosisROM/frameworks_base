/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.net;

import static android.net.ConnectivityManager.TYPE_WIFI;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.service.NetworkIdentityProto;
import android.telephony.Annotation.NetworkType;
import android.util.proto.ProtoOutputStream;

import java.util.Objects;

/**
 * Network definition that includes strong identity. Analogous to combining
 * {@link NetworkInfo} and an IMSI.
 *
 * @hide
 */
public class NetworkIdentity implements Comparable<NetworkIdentity> {
    private static final String TAG = "NetworkIdentity";

    public static final int SUBTYPE_COMBINED = -1;

    final int mType;
    final int mSubType;
    final String mSubscriberId;
    final String mNetworkId;
    final boolean mRoaming;
    final boolean mMetered;
    final boolean mDefaultNetwork;

    public NetworkIdentity(
            int type, int subType, String subscriberId, String networkId, boolean roaming,
            boolean metered, boolean defaultNetwork) {
        mType = type;
        mSubType = subType;
        mSubscriberId = subscriberId;
        mNetworkId = networkId;
        mRoaming = roaming;
        mMetered = metered;
        mDefaultNetwork = defaultNetwork;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mSubType, mSubscriberId, mNetworkId, mRoaming, mMetered,
                mDefaultNetwork);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NetworkIdentity) {
            final NetworkIdentity ident = (NetworkIdentity) obj;
            return mType == ident.mType && mSubType == ident.mSubType && mRoaming == ident.mRoaming
                    && Objects.equals(mSubscriberId, ident.mSubscriberId)
                    && Objects.equals(mNetworkId, ident.mNetworkId)
                    && mMetered == ident.mMetered
                    && mDefaultNetwork == ident.mDefaultNetwork;
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("{");
        builder.append("type=").append(mType);
        builder.append(", subType=");
        if (mSubType == SUBTYPE_COMBINED) {
            builder.append("COMBINED");
        } else {
            builder.append(mSubType);
        }
        if (mSubscriberId != null) {
            builder.append(", subscriberId=").append(scrubSubscriberId(mSubscriberId));
        }
        if (mNetworkId != null) {
            builder.append(", networkId=").append(mNetworkId);
        }
        if (mRoaming) {
            builder.append(", ROAMING");
        }
        builder.append(", metered=").append(mMetered);
        builder.append(", defaultNetwork=").append(mDefaultNetwork);
        return builder.append("}").toString();
    }

    public void dumpDebug(ProtoOutputStream proto, long tag) {
        final long start = proto.start(tag);

        proto.write(NetworkIdentityProto.TYPE, mType);

        // Not dumping mSubType, subtypes are no longer supported.

        if (mSubscriberId != null) {
            proto.write(NetworkIdentityProto.SUBSCRIBER_ID, scrubSubscriberId(mSubscriberId));
        }
        proto.write(NetworkIdentityProto.NETWORK_ID, mNetworkId);
        proto.write(NetworkIdentityProto.ROAMING, mRoaming);
        proto.write(NetworkIdentityProto.METERED, mMetered);
        proto.write(NetworkIdentityProto.DEFAULT_NETWORK, mDefaultNetwork);

        proto.end(start);
    }

    public int getType() {
        return mType;
    }

    public int getSubType() {
        return mSubType;
    }

    public String getSubscriberId() {
        return mSubscriberId;
    }

    public String getNetworkId() {
        return mNetworkId;
    }

    public boolean getRoaming() {
        return mRoaming;
    }

    public boolean getMetered() {
        return mMetered;
    }

    public boolean getDefaultNetwork() {
        return mDefaultNetwork;
    }

    /**
     * Scrub given IMSI on production builds.
     */
    public static String scrubSubscriberId(String subscriberId) {
        if (Build.IS_ENG) {
            return subscriberId;
        } else if (subscriberId != null) {
            // TODO: parse this as MCC+MNC instead of hard-coding
            return subscriberId.substring(0, Math.min(6, subscriberId.length())) + "...";
        } else {
            return "null";
        }
    }

    /**
     * Scrub given IMSI on production builds.
     */
    public static String[] scrubSubscriberId(String[] subscriberId) {
        if (subscriberId == null) return null;
        final String[] res = new String[subscriberId.length];
        for (int i = 0; i < res.length; i++) {
            res[i] = NetworkIdentity.scrubSubscriberId(subscriberId[i]);
        }
        return res;
    }

    /**
     * Build a {@link NetworkIdentity} from the given {@link NetworkState} and {@code subType},
     * assuming that any mobile networks are using the current IMSI. The subType if applicable,
     * should be set as one of the TelephonyManager.NETWORK_TYPE_* constants, or
     * {@link android.telephony.TelephonyManager#NETWORK_TYPE_UNKNOWN} if not.
     */
    public static NetworkIdentity buildNetworkIdentity(Context context, NetworkState state,
            boolean defaultNetwork, @NetworkType int subType) {
        final int type = state.networkInfo.getType();

        String subscriberId = null;
        String networkId = null;
        boolean roaming = !state.networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        boolean metered = !state.networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

        subscriberId = state.subscriberId;

        if (type == TYPE_WIFI) {
            if (state.networkCapabilities.getSsid() != null) {
                networkId = state.networkCapabilities.getSsid();
                if (networkId == null) {
                    // TODO: Figure out if this code path never runs. If so, remove them.
                    final WifiManager wifi = (WifiManager) context.getSystemService(
                            Context.WIFI_SERVICE);
                    final WifiInfo info = wifi.getConnectionInfo();
                    networkId = info != null ? info.getSSID() : null;
                }
            }
        }

        return new NetworkIdentity(type, subType, subscriberId, networkId, roaming, metered,
                defaultNetwork);
    }

    @Override
    public int compareTo(NetworkIdentity another) {
        int res = Integer.compare(mType, another.mType);
        if (res == 0) {
            res = Integer.compare(mSubType, another.mSubType);
        }
        if (res == 0 && mSubscriberId != null && another.mSubscriberId != null) {
            res = mSubscriberId.compareTo(another.mSubscriberId);
        }
        if (res == 0 && mNetworkId != null && another.mNetworkId != null) {
            res = mNetworkId.compareTo(another.mNetworkId);
        }
        if (res == 0) {
            res = Boolean.compare(mRoaming, another.mRoaming);
        }
        if (res == 0) {
            res = Boolean.compare(mMetered, another.mMetered);
        }
        if (res == 0) {
            res = Boolean.compare(mDefaultNetwork, another.mDefaultNetwork);
        }
        return res;
    }
}

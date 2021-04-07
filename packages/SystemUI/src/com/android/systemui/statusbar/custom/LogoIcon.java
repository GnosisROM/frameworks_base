/*
 * Copyright (C) 2021 The Gnosis Project
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

package com.android.systemui.statusbar.custom;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;

public class LogoIcon extends ImageView {

    private Context mContext;

    private boolean mAttached;
    private int mLogoStyle;
    private int mTintColor = Color.WHITE;
    private final Handler mHandler = new Handler();
    private ContentResolver mContentResolver;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_STYLE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    public LogoIcon(Context context) {
        this(context, null);
    }

    public LogoIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        mContext = context;
        mSettingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) {
            return;
        }
        mAttached = true;
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) {
            return;
        }
        mAttached = false;
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
    }

    public void updateLogo() {
        Drawable drawable = null;

        if (mLogoStyle == 0) {
            drawable = mContext.getDrawable(R.drawable.ic_gnosis_logo);
        } else if (mLogoStyle == 1) {
            drawable = mContext.getDrawable(R.drawable.ic_android_logo);
        } else if (mLogoStyle == 2) {
            drawable = mContext.getDrawable(R.drawable.ic_apple_logo);
        } else if (mLogoStyle == 3) {
            drawable = mContext.getDrawable(R.drawable.ic_assassin);
        } else if (mLogoStyle == 4) {
            drawable = mContext.getDrawable(R.drawable.ic_batman);
        } else if (mLogoStyle == 5) {
            drawable = mContext.getDrawable(R.drawable.ic_beats);
        } else if (mLogoStyle == 6) {
            drawable = mContext.getDrawable(R.drawable.ic_biohazard);
        } else if (mLogoStyle == 7) {
            drawable = mContext.getDrawable(R.drawable.ic_blackberry);
        } else if (mLogoStyle == 8) {
            drawable = mContext.getDrawable(R.drawable.ic_blogger);
        } else if (mLogoStyle == 9) {
            drawable = mContext.getDrawable(R.drawable.ic_bomb);
        } else if (mLogoStyle == 10) {
            drawable = mContext.getDrawable(R.drawable.ic_brain);
        } else if (mLogoStyle == 11) {
            drawable = mContext.getDrawable(R.drawable.ic_cake);
        } else if (mLogoStyle == 12) {
            drawable = mContext.getDrawable(R.drawable.ic_cannabis);
        } else if (mLogoStyle == 13) {
            drawable = mContext.getDrawable(R.drawable.ic_death_star);
        } else if (mLogoStyle == 14) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon);
        } else if (mLogoStyle == 15) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_cool);
        } else if (mLogoStyle == 16) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_dead);
        } else if (mLogoStyle == 17) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_devil);
        } else if (mLogoStyle == 18) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_happy);
        } else if (mLogoStyle == 19) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_neutral);
        } else if (mLogoStyle == 20) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_poop);
        } else if (mLogoStyle == 21) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_sad);
        } else if (mLogoStyle == 22) {
            drawable = mContext.getDrawable(R.drawable.ic_emoticon_tongue);
        } else if (mLogoStyle == 23) {
            drawable = mContext.getDrawable(R.drawable.ic_fire);
        } else if (mLogoStyle == 24) {
            drawable = mContext.getDrawable(R.drawable.ic_flask);
        } else if (mLogoStyle == 25) {
            drawable = mContext.getDrawable(R.drawable.ic_gender_female);
        } else if (mLogoStyle == 26) {
            drawable = mContext.getDrawable(R.drawable.ic_gender_male);
        } else if (mLogoStyle == 27) {
            drawable = mContext.getDrawable(R.drawable.ic_gender_male_female);
        } else if (mLogoStyle == 28) {
            drawable = mContext.getDrawable(R.drawable.ic_ghost);
        } else if (mLogoStyle == 29) {
            drawable = mContext.getDrawable(R.drawable.ic_google);
        } else if (mLogoStyle == 30) {
            drawable = mContext.getDrawable(R.drawable.ic_green_lantern);
        } else if (mLogoStyle == 31) {
            drawable = mContext.getDrawable(R.drawable.ic_guitar_acoustic);
        } else if (mLogoStyle == 32) {
            drawable = mContext.getDrawable(R.drawable.ic_guitar_electric);
        } else if (mLogoStyle == 33) {
            drawable = mContext.getDrawable(R.drawable.ic_heart);
        } else if (mLogoStyle == 34) {
            drawable = mContext.getDrawable(R.drawable.ic_human_female);
        } else if (mLogoStyle == 35) {
            drawable = mContext.getDrawable(R.drawable.ic_human_male);
        } else if (mLogoStyle == 36) {
            drawable = mContext.getDrawable(R.drawable.ic_human_male_female);
        } else if (mLogoStyle == 37) {
            drawable = mContext.getDrawable(R.drawable.ic_incognito);
        } else if (mLogoStyle == 38) {
            drawable = mContext.getDrawable(R.drawable.ic_ios_logo);
        } else if (mLogoStyle == 39) {
            drawable = mContext.getDrawable(R.drawable.ic_ironman);
        } else if (mLogoStyle == 40) {
            drawable = mContext.getDrawable(R.drawable.ic_linux);
        } else if (mLogoStyle == 41) {
            drawable = mContext.getDrawable(R.drawable.ic_lock);
        } else if (mLogoStyle == 42) {
            drawable = mContext.getDrawable(R.drawable.ic_music);
        } else if (mLogoStyle == 43) {
            drawable = mContext.getDrawable(R.drawable.ic_ninja);
        } else if (mLogoStyle == 44) {
            drawable = mContext.getDrawable(R.drawable.ic_pac_man);
        } else if (mLogoStyle == 45) {
            drawable = mContext.getDrawable(R.drawable.ic_peace);
        } else if (mLogoStyle == 46) {
            drawable = mContext.getDrawable(R.drawable.ic_phantom);
        } else if (mLogoStyle == 47) {
            drawable = mContext.getDrawable(R.drawable.ic_robot);
        } else if (mLogoStyle == 48) {
            drawable = mContext.getDrawable(R.drawable.ic_skull);
        } else if (mLogoStyle == 49) {
            drawable = mContext.getDrawable(R.drawable.ic_smoking);
        } else if (mLogoStyle == 50) {
            drawable = mContext.getDrawable(R.drawable.ic_trinity);
        } else if (mLogoStyle == 51) {
            drawable = mContext.getDrawable(R.drawable.ic_wallet);
        } else if (mLogoStyle == 52) {
            drawable = mContext.getDrawable(R.drawable.ic_windows);
        } else if (mLogoStyle == 53) {
            drawable = mContext.getDrawable(R.drawable.ic_wu_tang);
        } else if (mLogoStyle == 54) {
            drawable = mContext.getDrawable(R.drawable.ic_xbox);
        } else if (mLogoStyle == 55) {
            drawable = mContext.getDrawable(R.drawable.ic_xbox_controller);
        } else if (mLogoStyle == 56) {
            drawable = mContext.getDrawable(R.drawable.ic_yin_yang);
        }

        setImageDrawable(null);

        clearColorFilter();

        drawable.setTint(mTintColor);
        setImageDrawable(drawable);
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mLogoStyle = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_LOGO_STYLE, 0);
        updateLogo();
    }
}

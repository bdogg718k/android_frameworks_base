/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.autofill.ui;

import static com.android.server.autofill.Helper.sDebug;

import android.annotation.NonNull;
import android.app.Dialog;
import android.content.Context;
import android.content.IntentSender;
import android.os.Handler;
import android.service.autofill.SaveInfo;
import android.text.Html;
import android.util.ArraySet;
import android.util.Slog;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.R;
import com.android.server.UiThread;

/**
 * Autofill Save Prompt
 */
final class SaveUi {

    private static final String TAG = "AutofillSaveUi";

    public interface OnSaveListener {
        void onSave();
        void onCancel(IntentSender listener);
        void onDestroy();
    }

    private class OneTimeListener implements OnSaveListener {

        private final OnSaveListener mRealListener;
        private boolean mDone;

        OneTimeListener(OnSaveListener realListener) {
            mRealListener = realListener;
        }

        @Override
        public void onSave() {
            if (sDebug) Slog.d(TAG, "onSave(): " + mDone);
            if (mDone) {
                return;
            }
            mDone = true;
            mRealListener.onSave();
        }

        @Override
        public void onCancel(IntentSender listener) {
            if (sDebug) Slog.d(TAG, "onCancel(): " + mDone);
            if (mDone) {
                return;
            }
            mDone = true;
            mRealListener.onCancel(listener);
        }

        @Override
        public void onDestroy() {
            if (sDebug) Slog.d(TAG, "onDestroy(): " + mDone);
            if (mDone) {
                return;
            }
            mDone = true;
            mRealListener.onDestroy();
        }
    }

    private final Handler mHandler = UiThread.getHandler();

    private final @NonNull Dialog mDialog;

    private final @NonNull OneTimeListener mListener;

    private boolean mDestroyed;

    SaveUi(@NonNull Context context, @NonNull CharSequence providerLabel, @NonNull SaveInfo info,
            @NonNull OnSaveListener listener) {
        mListener = new OneTimeListener(listener);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view = inflater.inflate(R.layout.autofill_save, null);

        final TextView titleView = (TextView) view.findViewById(R.id.autofill_save_title);

        final ArraySet<String> types = new ArraySet<>(3);
        final int type = info.getType();

        if ((type & SaveInfo.SAVE_DATA_TYPE_PASSWORD) != 0) {
            types.add(context.getString(R.string.autofill_save_type_password));
        }
        if ((type & SaveInfo.SAVE_DATA_TYPE_ADDRESS) != 0) {
            types.add(context.getString(R.string.autofill_save_type_address));
        }
        if ((type & SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD) != 0) {
            types.add(context.getString(R.string.autofill_save_type_credit_card));
        }
        if ((type & SaveInfo.SAVE_DATA_TYPE_USERNAME) != 0) {
            types.add(context.getString(R.string.autofill_save_type_username));
        }
        if ((type & SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS) != 0) {
            types.add(context.getString(R.string.autofill_save_type_email_address));
        }

        final CharSequence title;
        switch (types.size()) {
            case 1:
                title = Html.fromHtml(context.getString(R.string.autofill_save_title_with_type,
                        types.valueAt(0), providerLabel), 0);
                break;
            case 2:
                title = Html.fromHtml(context.getString(R.string.autofill_save_title_with_2types,
                        types.valueAt(0), types.valueAt(1), providerLabel), 0);
                break;
            case 3:
                title = Html.fromHtml(context.getString(R.string.autofill_save_title_with_3types,
                        types.valueAt(0), types.valueAt(1), types.valueAt(2), providerLabel), 0);
                break;
            default:
                // Use generic if more than 3 or invalid type (size 0).
                title = Html.fromHtml(
                        context.getString(R.string.autofill_save_title, providerLabel), 0);
        }

        titleView.setText(title);
        final CharSequence subTitle = info.getDescription();
        if (subTitle != null) {
            final TextView subTitleView = (TextView) view.findViewById(R.id.autofill_save_subtitle);
            subTitleView.setText(subTitle);
            subTitleView.setVisibility(View.VISIBLE);
        }

        Slog.i(TAG, "Showing save dialog: " + title);
        if (sDebug) {
            Slog.d(TAG, "SubTitle: " + subTitle);
        }

        final TextView noButton = view.findViewById(R.id.autofill_save_no);
        if (info.getNegativeActionStyle() == SaveInfo.NEGATIVE_BUTTON_STYLE_REJECT) {
            noButton.setText(R.string.save_password_notnow);
        } else {
            noButton.setText(R.string.autofill_save_no);
        }
        noButton.setOnClickListener((v) -> mListener.onCancel(
                info.getNegativeActionListener()));

        final View yesButton = view.findViewById(R.id.autofill_save_yes);
        yesButton.setOnClickListener((v) -> mListener.onSave());

        final View closeButton = view.findViewById(R.id.autofill_save_close);
        closeButton.setOnClickListener((v) -> mListener.onCancel(
                info.getNegativeActionListener()));

        mDialog = new Dialog(context, R.style.Theme_Material_Panel);
        mDialog.setContentView(view);

        final Window window = mDialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER);
        window.setCloseOnTouchOutside(true);
        final WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.accessibilityTitle = context.getString(R.string.autofill_save_accessibility_title);

        mDialog.show();
    }

    void destroy() {
        throwIfDestroyed();
        mListener.onDestroy();
        mHandler.removeCallbacksAndMessages(mListener);
        mDialog.dismiss();
        mDestroyed = true;
    }

    private void throwIfDestroyed() {
        if (mDestroyed) {
            throw new IllegalStateException("cannot interact with a destroyed instance");
        }
    }
}

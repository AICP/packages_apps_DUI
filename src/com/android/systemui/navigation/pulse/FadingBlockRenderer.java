/**
 * Copyright 2011, Felix Palmer
 * Copyright (C) 2014 The TeamEos Project
 * Copyright (C) 2016 The DirtyUnicorns Project
 *
 * AOSP Navigation implementation by
 * @author: Randall Rushing <randall.rushing@gmail.com>
 *
 * Licensed under the MIT license:
 * http://creativecommons.org/licenses/MIT/
 *
 * Old school FFT renderer adapted from
 * @link https://github.com/felixpalmer/android-visualizer
 *
 */

package com.android.systemui.navigation.pulse;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.navigation.pulse.PulseController.PulseObserver;
import com.android.systemui.navigation.utils.ColorAnimator;

public class FadingBlockRenderer extends Renderer implements ColorAnimator.ColorAnimationListener {
    private static final int DEF_PAINT_ALPHA = (byte) 188;
    private byte[] mFFTBytes;
    private Paint mPaint;
    private Paint mFadePaint;
    private boolean mVertical;
    private boolean mLeftInLandscape;
    private float[] mFFTPoints;
    private byte rfk, ifk;
    private int dbValue;
    private float magnitude;
    private int mDivisions;
    private int mUserColor;
    private int mDbFuzzFactor;
    private int mDbFuzz;
    private int mPathEffect1;
    private int mPathEffect2;
    private Bitmap mCanvasBitmap;
    private Canvas mCanvas;
    private Matrix mMatrix;
    private int mWidth;
    private int mHeight;

    private ColorAnimator mLavaLamp;
    private LegacySettingsObserver mObserver;
    private boolean mLavaLampEnabled;
    private boolean mIsValidStream;

    public FadingBlockRenderer(Context context, Handler handler, PulseObserver callback) {
        super(context, handler, callback);
        mObserver = new LegacySettingsObserver(handler);
        mLavaLamp = new ColorAnimator();
        mLavaLamp.setColorAnimatorListener(this);
        mPaint = new Paint();
        mFadePaint = new Paint();
        mFadePaint.setColor(Color.argb(200, 255, 255, 255));
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));
        mMatrix = new Matrix();
        mDbFuzz = mContext.getResources().getInteger(R.integer.config_pulseDbFuzz);
        mObserver.updateSettings();
        mPaint.setAntiAlias(true);
        onSizeChanged(0, 0, 0, 0);
    }

    @Override
    public void onStreamAnalyzed(boolean isValid) {
        mIsValidStream = isValid;
        if (isValid) {
            onSizeChanged(0, 0, 0, 0);
            if (mLavaLampEnabled) {
                mLavaLamp.start();
            }
        }
    }

    @Override
    public void onFFTUpdate(byte[] bytes) {
        mFFTBytes = bytes;
        if (mFFTBytes != null) {
            if (mFFTPoints == null || mFFTPoints.length < mFFTBytes.length * 4) {
                mFFTPoints = new float[mFFTBytes.length * 4];
            }
            for (int i = 0; i < mFFTBytes.length / mDivisions; i++) {
                if (mVertical) {
                    mFFTPoints[i * 4 + 1] = i * 4 * mDivisions;
                    mFFTPoints[i * 4 + 3] = i * 4 * mDivisions;
                } else {
                    mFFTPoints[i * 4] = i * 4 * mDivisions;
                    mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                }
                rfk = mFFTBytes[mDivisions * i];
                ifk = mFFTBytes[mDivisions * i + 1];
                magnitude = (rfk * rfk + ifk * ifk);
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;
                if (mVertical) {
                    mFFTPoints[i * 4] = mLeftInLandscape ? 0 : mWidth;
                    mFFTPoints[i * 4 + 2] = mLeftInLandscape ? (dbValue * mDbFuzzFactor + mDbFuzz)
                            : (mWidth - (dbValue * mDbFuzzFactor + mDbFuzz));
                } else {
                    mFFTPoints[i * 4 + 1] = mHeight;
                    mFFTPoints[i * 4 + 3] = mHeight - (dbValue * mDbFuzzFactor + mDbFuzz);
                }
            }
        }
        mCanvas.drawLines(mFFTPoints, mPaint);
        mCanvas.drawPaint(mFadePaint);
        postInvalidate();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mCallback.getWidth() > 0 && mCallback.getHeight() > 0) {
            mWidth = mCallback.getWidth();
            mHeight = mCallback.getHeight();
            mVertical = mHeight > mWidth;
            mCanvasBitmap = Bitmap.createBitmap(mWidth, mHeight, Config.ARGB_8888);
            mCanvas = new Canvas(mCanvasBitmap);
        }
    }

    @Override
    public void onColorChanged(ColorAnimator colorAnimator, int color) {
        mPaint.setColor(applyPaintAlphaToColor(color));
    }

    @Override
    public void onStartAnimation(ColorAnimator colorAnimator, int firstColor) {
    }

    @Override
    public void onStopAnimation(ColorAnimator colorAnimator, int lastColor) {
        mPaint.setColor(applyPaintAlphaToColor(mUserColor));
    }

    @Override
    public void setLeftInLandscape(boolean leftInLandscape) {
        if (mLeftInLandscape != leftInLandscape) {
            mLeftInLandscape = leftInLandscape;
            onSizeChanged(0, 0, 0, 0);
        }
    }

    @Override
    public void destroy() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        mLavaLamp.stop();
        mCanvasBitmap = null;
    }

    @Override
    public void onVisualizerLinkChanged(boolean linked) {
        if (!linked) {
            mLavaLamp.stop();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(mCanvasBitmap, mMatrix, null);
    }

    private int applyPaintAlphaToColor(int color) {
        int opaqueColor = Color.rgb(Color.red(color),
                Color.green(color), Color.blue(color));
        return (DEF_PAINT_ALPHA << 24) | (opaqueColor & 0x00ffffff);
    }

    private class LegacySettingsObserver extends ContentObserver {
        public LegacySettingsObserver(Handler handler) {
            super(handler);
            register();
        }

        void register() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_COLOR), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.FLING_PULSE_LAVALAMP_SPEED), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIMEN), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_DIV), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_FILLED_BLOCK_SIZE), false,
                    this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_EMPTY_BLOCK_SIZE), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR), false,
                    this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }

        public void updateSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            mLavaLampEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
            mUserColor = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_COLOR,
                    mContext.getResources().getColor(R.color.config_pulseFillColor),
                    UserHandle.USER_CURRENT);
            if (!mLavaLampEnabled) {
                mPaint.setColor(applyPaintAlphaToColor(mUserColor));
            }
            int time = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.FLING_PULSE_LAVALAMP_SPEED, 10000,
                    UserHandle.USER_CURRENT);
            mLavaLamp.setAnimationTime(time);
            if (mLavaLampEnabled && mIsValidStream) {
                mLavaLamp.start();
            } else {
                mLavaLamp.stop();
            }
            int emptyBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_EMPTY_BLOCK_SIZE, 1,
                    UserHandle.USER_CURRENT);
            int customDimen = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIMEN, 14,
                    UserHandle.USER_CURRENT);
            int numDivision = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_DIV, 8,
                    UserHandle.USER_CURRENT);
            int fudgeFactor = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_CUSTOM_FUDGE_FACTOR, 2,
                    UserHandle.USER_CURRENT);
            int filledBlock = Settings.Secure.getIntForUser(
                    resolver, Settings.Secure.PULSE_FILLED_BLOCK_SIZE, 2,
                    UserHandle.USER_CURRENT);
            if (filledBlock == 0) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect1_1);
            }
            else if (filledBlock== 1) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect2_1);
            }
            else if (filledBlock == 2) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect_1);
            }
            else if (filledBlock == 3) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect3_1);
            }
            else if (filledBlock == 4) {
                mPathEffect1 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect4_1);
            }
            if (emptyBlock == 0) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect4_2);
            }
            else if (emptyBlock == 1) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect_2);
            }
            else if (emptyBlock == 2) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect1_2);
            }
            else if (emptyBlock == 3) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect2_2);
            }
            else if (emptyBlock == 4) {
                mPathEffect2 = mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathEffect3_2);
            }
            mPaint.setPathEffect(null);
            mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[] {
                    mPathEffect1,
                    mPathEffect2
            }, 0));
            if (customDimen == 0) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth1));
            }
            else if (customDimen == 1) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth2));
            }
            else if (customDimen == 2) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth3));
            }
            else if (customDimen == 3) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth4));
            }
            else if (customDimen == 4) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth5));
            }
            else if (customDimen == 5) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth6));
            }
            else if (customDimen == 6) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth7));
            }
            else if (customDimen == 7) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth));
            }
            else if (customDimen == 8) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth8));
            }
            else if (customDimen == 9) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth9));
            }
            else if (customDimen == 10) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth10));
            }
            else if (customDimen == 11) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth11));
            }
            else if (customDimen == 12) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth12));
            }
            else if (customDimen == 13) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth13));
            }
            else if (customDimen == 14) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth));
            }
            else if (customDimen == 15) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth14));
            }
            else if (customDimen == 16) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth15));
            }
            else if (customDimen == 17) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth16));
            }
            else if (customDimen == 18) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth17));
            }
            else if (customDimen == 19) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth18));
            }
	    else if (customDimen == 20) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth19));
            }
            else if (customDimen == 21) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth20));
            }
            else if (customDimen == 22) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth21));
            }
            else if (customDimen == 23) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth22));
            }
            else if (customDimen == 24) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth23));
            }
            else if (customDimen == 25) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth24));
            }
            else if (customDimen == 26) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth25));
            }
            else if (customDimen == 27) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth26));
            }
            else if (customDimen == 28) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth27));
            }
            else if (customDimen == 29) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth28));
            }
	    else if (customDimen == 30) {
                mPaint.setStrokeWidth(mContext.getResources().getDimensionPixelSize(
                        R.dimen.config_pulsePathStrokeWidth29));
            }
            if (numDivision == 1) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions1);
            }
            else if (numDivision == 2) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions2);
            }
            else if (numDivision == 3) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions3);
            }
            else if (numDivision == 4) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions4);
            }
            else if (numDivision == 5) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions5);
            }
            else if (numDivision == 6) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions6);
            }
            else if (numDivision == 7) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions7);
            }
            else if (numDivision == 8) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions);
            }
            else if (numDivision == 9) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions8);
            }
            else if (numDivision == 10) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions9);
            }
            else if (numDivision == 11) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions10);
            }
            else if (numDivision == 12) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions10);
            }
            else if (numDivision == 13) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions11);
            }
            else if (numDivision == 14) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions12);
            }
            else if (numDivision == 15) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions13);
            }
            else if (numDivision == 16) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions14);
            }
            else if (numDivision == 17) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions15);
            }
            else if (numDivision == 18) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions16);
            }
            else if (numDivision == 19) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions17);
            }
            else if (numDivision == 20) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions18);
            }
            else if (numDivision == 21) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions19);
            }
            else if (numDivision == 22) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions20);
            }
            else if (numDivision == 23) {
                mDivisions = mContext.getResources().getInteger(R.integer.config_pulseDivisions21);
            }
            if (fudgeFactor == 0) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor1);
            }
            else if (fudgeFactor == 1) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor2);
            }
            else if (fudgeFactor == 2) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor);
            }
            else if (fudgeFactor == 3) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor3);
            }
            else if (fudgeFactor == 4) {
                mDbFuzzFactor = mContext.getResources().getInteger(
                        R.integer.config_pulseDbFuzzFactor4);
            }
        }
    }
}

package com.android.systemui.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.RenderNodeAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.navigation.smartbar.SmartButtonView;

public class OpaLayout extends FrameLayout {

    private static final int ANIMATION_STATE_NONE = 0;
    private static final int ANIMATION_STATE_DIAMOND = 1;
    private static final int ANIMATION_STATE_RETRACT = 2;
    private static final int ANIMATION_STATE_OTHER = 3;

    private static final int MIN_DIAMOND_DURATION = 100;
    private static final int COLLAPSE_ANIMATION_DURATION_RY = 83;
    private static final int COLLAPSE_ANIMATION_DURATION_BG = 100;
    private static final int LINE_ANIMATION_DURATION_Y = 275;
    private static final int LINE_ANIMATION_DURATION_X = 133;
    private static final int RETRACT_ANIMATION_DURATION = 300;
    private static final int DIAMOND_ANIMATION_DURATION = 200;
    private static final int HALO_ANIMATION_DURATION = 100;

    private static final int DOTS_RESIZE_DURATION = 200;
    private static final int HOME_RESIZE_DURATION = 83;

    private static final int HOME_REAPPEAR_ANIMATION_OFFSET = 33;
    private static final int HOME_REAPPEAR_DURATION = 150;

    private static final float DIAMOND_DOTS_SCALE_FACTOR = 0.8f;
    private static final float DIAMOND_HOME_SCALE_FACTOR = 0.625f;
    private static final float HALO_SCALE_FACTOR = 0.47619048f;

    private int mAnimationState;
    private final ArraySet<Animator> mCurrentAnimators;

    private boolean mIsLandscape;
    private boolean mIsPressed;
    private boolean mLongClicked;
    private boolean mOpaEnabled;
    private boolean mEditMode;
    private boolean mOpaHomeOnly;
    private boolean mIsHomeButton;
    private long mStartTime;

    private View mRed;
    private View mBlue;
    private View mGreen;
    private View mYellow;
    private View mSmartButton;

    private View mTop;
    private View mRight;
    private View mLeft;
    private View mBottom;

    private final Runnable mCheckLongPress;
    private final Runnable mRetract;

    private final Interpolator mRetractInterpolator;
    private final Interpolator mCollapseInterpolator;
    private final Interpolator mDiamondInterpolator;
    private final Interpolator mDotsFullSizeInterpolator;
    private final Interpolator mFastOutSlowInInterpolator;
    private final Interpolator mHomeDisappearInterpolator;
    private static Context mContext;

    public OpaLayout(Context context) {
        this(context, null);
    }

    public OpaLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OpaLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
        mContext = context;
    }

    public OpaLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;
        mHomeDisappearInterpolator = new PathInterpolator(0.8f, 0f, 1f, 1f);
        mCollapseInterpolator = Interpolators.FAST_OUT_LINEAR_IN;
        mDotsFullSizeInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        mRetractInterpolator = new PathInterpolator(0.4f, 0f, 0f, 1f);
        mDiamondInterpolator = new PathInterpolator(0.2f, 0f, 0.2f, 1f);
        mCheckLongPress = new Runnable() {
            @Override
            public void run() {
                if (mIsPressed) {
                    mLongClicked = true;
                }
            }
        };
        mRetract = new Runnable() {
            @Override
            public void run() {
                cancelCurrentAnimation();
                startRetractAnimation();
                setOpaVisibility(false);
            }
        };
        mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
        mCurrentAnimators = new ArraySet<Animator>();
    }

    private void startAll(ArraySet<Animator> animators) {
        setOpaVisibility(true);
        for(int i=0; i < animators.size(); i++) {
            Animator curAnim = (Animator) mCurrentAnimators.valueAt(i);
            curAnim.start();
        }
    }

    public static int setYAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.OPA_ANIM_DURATION_Y, LINE_ANIMATION_DURATION_Y,
                UserHandle.USER_CURRENT);
    }

    public static int setXAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.OPA_ANIM_DURATION_X, LINE_ANIMATION_DURATION_X,
                UserHandle.USER_CURRENT);
    }

    public static int setBGAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.COLLAPSE_ANIMATION_DURATION_BG, COLLAPSE_ANIMATION_DURATION_BG,
                UserHandle.USER_CURRENT);
    }

    public static int setRYAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.COLLAPSE_ANIMATION_DURATION_RY, COLLAPSE_ANIMATION_DURATION_RY,
                UserHandle.USER_CURRENT);
    }

    public static int setRetractAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.RETRACT_ANIMATION_DURATION, RETRACT_ANIMATION_DURATION,
                UserHandle.USER_CURRENT);
    }

    public static int setDiamondAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.DIAMOND_ANIMATION_DURATION, DIAMOND_ANIMATION_DURATION,
                UserHandle.USER_CURRENT);
    }

    public static int setDotsAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.DOTS_RESIZE_DURATION, DOTS_RESIZE_DURATION,
                UserHandle.USER_CURRENT);
    }

    public static int setHomeResizeAnimationDuration() {
       return Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.HOME_RESIZE_DURATION, HOME_RESIZE_DURATION,
                UserHandle.USER_CURRENT);
    }

    private void startCollapseAnimation() {
        mCurrentAnimators.clear();
        mCurrentAnimators.addAll(getCollapseAnimatorSet());
        mAnimationState = OpaLayout.ANIMATION_STATE_OTHER;
        startAll(mCurrentAnimators);
    }

    private void startDiamondAnimation() {
        mCurrentAnimators.clear();
        mCurrentAnimators.addAll(getDiamondAnimatorSet());
        mAnimationState = OpaLayout.ANIMATION_STATE_DIAMOND;
        startAll(mCurrentAnimators);
    }

    private void startLineAnimation() {
        mCurrentAnimators.clear();
        mCurrentAnimators.addAll(getLineAnimatorSet());
        mAnimationState = OpaLayout.ANIMATION_STATE_OTHER;
        startAll(mCurrentAnimators);
    }

    private void startRetractAnimation() {
        mCurrentAnimators.clear();
        mCurrentAnimators.addAll(getRetractAnimatorSet());
        mAnimationState = OpaLayout.ANIMATION_STATE_RETRACT;
        startAll(mCurrentAnimators);
    }

    private void cancelCurrentAnimation() {
        if(mCurrentAnimators.isEmpty())
            return;
        for(int i=0; i < mCurrentAnimators.size(); i++) {
            Animator curAnim = (Animator) mCurrentAnimators.valueAt(i);
            curAnim.removeAllListeners();
            curAnim.cancel();
        }
        mCurrentAnimators.clear();
        mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
    }

    private void endCurrentAnimation() {
        if(mCurrentAnimators.isEmpty())
            return;
        for(int i=0; i < mCurrentAnimators.size(); i++) {
            Animator curAnim = (Animator) mCurrentAnimators.valueAt(i);
            curAnim.removeAllListeners();
            curAnim.end();
        }
        mCurrentAnimators.clear();
        mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
    }

    private ArraySet<Animator> getCollapseAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        Animator animator;
        if (mIsLandscape) {
            animator = getDeltaAnimatorY(mRed, mCollapseInterpolator, -getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.setRYAnimationDuration());
        }
        else {
            animator = getDeltaAnimatorX(mRed, mCollapseInterpolator, getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.setRYAnimationDuration());
        }
        set.add(animator);
        set.add(getScaleAnimatorX(mRed, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        set.add(getScaleAnimatorY(mRed, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        Animator animator2;
        if (mIsLandscape) {
            animator2 = getDeltaAnimatorY(mBlue, mCollapseInterpolator, -getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.setBGAnimationDuration());
        }
        else {
            animator2 = getDeltaAnimatorX(mBlue, mCollapseInterpolator, getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.setBGAnimationDuration());
        }
        set.add(animator2);
        set.add(getScaleAnimatorX(mBlue, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        set.add(getScaleAnimatorY(mBlue, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        Animator animator3;
        if (mIsLandscape) {
            animator3 = getDeltaAnimatorY(mYellow, mCollapseInterpolator, getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.setRYAnimationDuration());
        }
        else {
            animator3 = getDeltaAnimatorX(mYellow, mCollapseInterpolator, -getPxVal(R.dimen.opa_line_x_collapse_ry), OpaLayout.setRYAnimationDuration());
        }
        set.add(animator3);
        set.add(getScaleAnimatorX(mYellow, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        set.add(getScaleAnimatorY(mYellow, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        Animator animator4;
        if (mIsLandscape) {
            animator4 = getDeltaAnimatorY(mGreen, mCollapseInterpolator, getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.setBGAnimationDuration());
        }
        else {
            animator4 = getDeltaAnimatorX(mGreen, mCollapseInterpolator, -getPxVal(R.dimen.opa_line_x_collapse_bg), OpaLayout.setBGAnimationDuration());
        }
        set.add(animator4);
        set.add(getScaleAnimatorX(mGreen, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        set.add(getScaleAnimatorY(mGreen, 1.0f, OpaLayout.setDotsAnimationDuration(), mDotsFullSizeInterpolator));
        final Animator scaleAnimatorX = getScaleAnimatorX(mSmartButton, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, mFastOutSlowInInterpolator);
        final Animator scaleAnimatorY = getScaleAnimatorY(mSmartButton, 1.0f, OpaLayout.HOME_REAPPEAR_DURATION, mFastOutSlowInInterpolator);
        scaleAnimatorX.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        scaleAnimatorY.setStartDelay(OpaLayout.HOME_REAPPEAR_ANIMATION_OFFSET);
        set.add(scaleAnimatorX);
        set.add(scaleAnimatorY);
        getLongestAnim((set)).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationEnd(final Animator animator) {
                mCurrentAnimators.clear();
                mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
                setOpaVisibility(false);
            }
        });
        return set;
    }

    private ArraySet<Animator> getDiamondAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        set.add(getDeltaAnimatorY(mTop, mDiamondInterpolator, -getPxVal(R.dimen.opa_diamond_translation), OpaLayout.setDiamondAnimationDuration()));
        set.add(getScaleAnimatorX(mTop, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorY(mTop, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getDeltaAnimatorY(mBottom, mDiamondInterpolator, getPxVal(R.dimen.opa_diamond_translation), OpaLayout.setDiamondAnimationDuration()));
        set.add(getScaleAnimatorX(mBottom, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorY(mBottom, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getDeltaAnimatorX(mLeft, mDiamondInterpolator, -getPxVal(R.dimen.opa_diamond_translation), OpaLayout.setDiamondAnimationDuration()));
        set.add(getScaleAnimatorX(mLeft, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorY(mLeft, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getDeltaAnimatorX(mRight, mDiamondInterpolator, getPxVal(R.dimen.opa_diamond_translation), OpaLayout.setDiamondAnimationDuration()));
        set.add(getScaleAnimatorX(mRight, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorY(mRight, OpaLayout.DIAMOND_DOTS_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorX(mSmartButton, OpaLayout.DIAMOND_HOME_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        set.add(getScaleAnimatorY(mSmartButton, OpaLayout.DIAMOND_HOME_SCALE_FACTOR, OpaLayout.setDiamondAnimationDuration(), mFastOutSlowInInterpolator));
        getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationCancel(final Animator animator) {
                mCurrentAnimators.clear();
            }

            public void onAnimationEnd(final Animator animator) {
                startLineAnimation();
            }
        });
        return set;
    }

    private ArraySet<Animator> getLineAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        if (mIsLandscape) {
            set.add(getDeltaAnimatorY(mRed, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorX(mRed, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_y_translation), OpaLayout.setXAnimationDuration()));
            set.add(getDeltaAnimatorY(mBlue, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorY(mYellow, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorX(mYellow, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_y_translation), OpaLayout.setXAnimationDuration()));
            set.add(getDeltaAnimatorY(mGreen, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.setYAnimationDuration()));
        }
        else {
            set.add(getDeltaAnimatorX(mRed, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorY(mRed, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_y_translation), OpaLayout.setXAnimationDuration()));
            set.add(getDeltaAnimatorX(mBlue, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorX(mYellow, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_x_trans_ry), OpaLayout.setYAnimationDuration()));
            set.add(getDeltaAnimatorY(mYellow, mFastOutSlowInInterpolator, -getPxVal(R.dimen.opa_line_y_translation), OpaLayout.setXAnimationDuration()));
            set.add(getDeltaAnimatorX(mGreen, mFastOutSlowInInterpolator, getPxVal(R.dimen.opa_line_x_trans_bg), OpaLayout.setYAnimationDuration()));
        }
        set.add(getScaleAnimatorX(mSmartButton, 0.0f, OpaLayout.setHomeResizeAnimationDuration(), mHomeDisappearInterpolator));
        set.add(getScaleAnimatorY(mSmartButton, 0.0f, OpaLayout.setHomeResizeAnimationDuration(), mHomeDisappearInterpolator));
        getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationCancel(final Animator animator) {
                mCurrentAnimators.clear();
            }

            public void onAnimationEnd(final Animator animator) {
                startCollapseAnimation();
            }
        });
        return set;
    }

    private ArraySet<Animator> getRetractAnimatorSet() {
        final ArraySet<Animator> set = new ArraySet<Animator>();
        set.add(getTranslationAnimatorX(mRed, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getTranslationAnimatorY(mRed, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getScaleAnimatorX(mRed, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorY(mRed, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getTranslationAnimatorX(mBlue, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getTranslationAnimatorY(mBlue, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getScaleAnimatorX(mBlue, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorY(mBlue, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getTranslationAnimatorX(mGreen, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getTranslationAnimatorY(mGreen, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getScaleAnimatorX(mGreen, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorY(mGreen, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getTranslationAnimatorX(mYellow, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getTranslationAnimatorY(mYellow, mRetractInterpolator, OpaLayout.setRetractAnimationDuration()));
        set.add(getScaleAnimatorX(mYellow, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorY(mYellow, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorX(mSmartButton, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        set.add(getScaleAnimatorY(mSmartButton, 1.0f, OpaLayout.setRetractAnimationDuration(), mRetractInterpolator));
        getLongestAnim(set).addListener((Animator.AnimatorListener)new AnimatorListenerAdapter() {
            public void onAnimationEnd(final Animator animator) {
                mCurrentAnimators.clear();
                mAnimationState = OpaLayout.ANIMATION_STATE_NONE;
            }
        });
        return set;
    }

    private float getPxVal(int id) {
        return getResources().getDimensionPixelOffset(id);
    }

    private Animator getDeltaAnimatorX(View v, Interpolator interpolator, float deltaX, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(8, (int) (v.getX() + deltaX));
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getDeltaAnimatorY(View v, Interpolator interpolator, float deltaY, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(9, (int) (v.getY() + deltaY));
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getScaleAnimatorX(View v, float factor, int duration, Interpolator interpolator) {
        RenderNodeAnimator anim = new RenderNodeAnimator(3, factor);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getScaleAnimatorY(View v, float factor, int duration, Interpolator interpolator) {
        RenderNodeAnimator anim = new RenderNodeAnimator(4, factor);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getTranslationAnimatorX(View v, Interpolator interpolator, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(0, 0);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getTranslationAnimatorY(View v, Interpolator interpolator, int duration) {
        RenderNodeAnimator anim = new RenderNodeAnimator(1, 0);
        anim.setTarget(v);
        anim.setInterpolator(interpolator);
        anim.setDuration(duration);
        return anim;
    }

    private Animator getLongestAnim(ArraySet<Animator> animators) {
        long longestDuration = -1;
        Animator longestAnim = null;

        for(int i=0; i < animators.size(); i++) {
            Animator a = (Animator) animators.valueAt(i);
            if(a.getTotalDuration() > longestDuration) {
                longestDuration = a.getTotalDuration();
                longestAnim = a;
            }
        }
        return longestAnim;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();

        mRed = findViewById(R.id.red);
        mBlue = findViewById(R.id.blue);
        mYellow = findViewById(R.id.yellow);
        mGreen = findViewById(R.id.green);
        mSmartButton = findViewById(R.id.smartbutton);

        setOpaVisibility(false);
    }

    public SmartButtonView getButton() {
        return (SmartButtonView)mSmartButton;
    }

    public void setEditMode(boolean enabled){
        mEditMode = enabled;
    }

    public void startDownAction() {
        if (!mOpaEnabled || mEditMode) {
            return;
        }
        if (!mCurrentAnimators.isEmpty()) {
            if (mAnimationState != OpaLayout.ANIMATION_STATE_RETRACT) {
                return;
            }
            endCurrentAnimation();
        }
        mStartTime = SystemClock.elapsedRealtime();
        mLongClicked = false;
        mIsPressed = true;
        startDiamondAnimation();
        removeCallbacks(mCheckLongPress);
        postDelayed(mCheckLongPress, (long)ViewConfiguration.getLongPressTimeout());
    }

    public void startCancelAction() {
        if (!mOpaEnabled || mEditMode) {
            return;
        }
        if (mAnimationState == OpaLayout.ANIMATION_STATE_DIAMOND) {
            final long elapsedRealtime = SystemClock.elapsedRealtime();
            removeCallbacks(mRetract);
            postDelayed(mRetract, 100L - (elapsedRealtime - mStartTime));
            removeCallbacks(mCheckLongPress);
            return;
        }
        int n;
        if (!mIsPressed || mLongClicked) {
            n = 0;
        }
        else {
            n = 1;
        }
        mIsPressed = false;
        if (n != 0) {
            mRetract.run();
            return;
        }
    }

    public void setLandscape(boolean landscape) {
        mIsLandscape = landscape;
        if (mIsLandscape) {
            mTop = mGreen;
            mBottom = mBlue;
            mRight = mYellow;
            mLeft = mRed;
            return;
        }
        mTop = mRed;
        mBottom = mYellow;
        mLeft = mBlue;
        mRight = mGreen;
    }

    public void setOpaEnabled(boolean enabled) {
        mOpaEnabled = enabled;
    }

    public void setOpaVisibility(boolean enabled) {
        int visibility = enabled ? View.VISIBLE : View.INVISIBLE;
        mBlue.setVisibility(((mOpaHomeOnly && mIsHomeButton) || !mOpaHomeOnly) ? visibility : View.INVISIBLE);
        mRed.setVisibility(((mOpaHomeOnly && mIsHomeButton) || !mOpaHomeOnly) ? visibility : View.INVISIBLE);
        mYellow.setVisibility(((mOpaHomeOnly && mIsHomeButton) || !mOpaHomeOnly) ? visibility : View.INVISIBLE);
        mGreen.setVisibility(((mOpaHomeOnly && mIsHomeButton) || !mOpaHomeOnly) ? visibility : View.INVISIBLE);
    }

    public void setOpaVisibilityHome(boolean opaHomeOnly, boolean isHomeButton) {
        mOpaHomeOnly = opaHomeOnly;
        mIsHomeButton = isHomeButton;
    }
}

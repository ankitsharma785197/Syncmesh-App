package com.ankit.syncmesh.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ankit.syncmesh.R;

/**
 * Presentation-only guided product tour. Dims the screen, spotlights one target view at a time,
 * and shows an explanation card with Back / Next / Skip. Steps are resolved lazily through a
 * {@link Controller} so the tour can move across tabs before highlighting a control. It never
 * touches business logic — it only reads view positions and draws on top of the decor view.
 */
@SuppressLint("ViewConstructor")
public class TourOverlayView extends FrameLayout {

    /** Supplies the tour steps and prepares (navigates to) each one on demand. */
    public interface Controller {
        int stepCount();

        /** Navigate to / lay out step {@code index}, then hand the resolved target to {@code ready}. */
        void prepareStep(int index, @NonNull ReadyCallback ready);

        void onTourFinished(boolean completed);
    }

    public interface ReadyCallback {
        void onReady(@Nullable View target, int titleRes, int descRes);
    }

    private final Paint scrimPaint = new Paint();
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spotlight = new RectF();
    private final Controller controller;
    private final View card;
    private final TextView stepCounter;
    private final TextView title;
    private final TextView desc;
    private final TextView nextButton;
    private final TextView backButton;
    private final TextView skipButton;
    private final View buttonSpacer;
    private final float density;
    private int index;
    private boolean hasSpotlight;

    public TourOverlayView(@NonNull Context context, @NonNull Controller controller) {
        super(context);
        this.controller = controller;
        density = getResources().getDisplayMetrics().density;

        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        scrimPaint.setColor(0xCC000000);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        card = LayoutInflater.from(context).inflate(R.layout.view_tour_card, this, false);
        addView(card);
        stepCounter = card.findViewById(R.id.tour_step_counter);
        title = card.findViewById(R.id.tour_title);
        desc = card.findViewById(R.id.tour_desc);
        nextButton = card.findViewById(R.id.tour_next);
        backButton = card.findViewById(R.id.tour_back);
        skipButton = card.findViewById(R.id.tour_skip);
        buttonSpacer = card.findViewById(R.id.tour_spacer);

        skipButton.setOnClickListener(v -> controller.onTourFinished(false));
        backButton.setOnClickListener(v -> {
            if (index > 0) {
                goTo(index - 1);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (index >= controller.stepCount() - 1) {
                controller.onTourFinished(true);
            } else {
                goTo(index + 1);
            }
        });
    }

    public void start() {
        goTo(0);
    }

    private void goTo(int newIndex) {
        index = newIndex;
        // Hide the card until the target is prepared to avoid a flash in the old position.
        card.setVisibility(View.INVISIBLE);
        hasSpotlight = false;
        invalidate();
        controller.prepareStep(index, this::render);
    }

    private void render(@Nullable View target, int titleRes, int descRes) {
        stepCounter.setText(getContext().getString(
                R.string.onboarding_step_counter, index + 1, controller.stepCount()));
        title.setText(titleRes);
        desc.setText(descRes);
        // First step shows a single full-width Next button; later steps restore Skip/Back/Next.
        boolean firstStep = index == 0;
        skipButton.setVisibility(firstStep ? View.GONE : View.VISIBLE);
        buttonSpacer.setVisibility(firstStep ? View.GONE : View.VISIBLE);
        backButton.setVisibility(firstStep ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams nextParams =
                (LinearLayout.LayoutParams) nextButton.getLayoutParams();
        nextParams.width = firstStep
                ? LinearLayout.LayoutParams.MATCH_PARENT
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        nextParams.setMarginStart(firstStep
                ? 0 : getResources().getDimensionPixelSize(R.dimen.space_sm));
        nextButton.setLayoutParams(nextParams);
        nextButton.setText(index >= controller.stepCount() - 1
                ? R.string.tour_finish : R.string.onboarding_next);

        boolean targetBottomHalf;
        int screenHeight = getHeight() > 0 ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;

        if (target != null && target.getWidth() > 0 && target.isShown()) {
            int[] location = new int[2];
            target.getLocationInWindow(location);
            int[] self = new int[2];
            getLocationInWindow(self);
            float left = location[0] - self[0];
            float top = location[1] - self[1];
            float pad = dp(6);
            spotlight.set(left - pad, top - pad,
                    left + target.getWidth() + pad, top + target.getHeight() + pad);
            hasSpotlight = true;
            targetBottomHalf = (top + target.getHeight() / 2f) > screenHeight / 2f;
        } else {
            hasSpotlight = false;
            targetBottomHalf = false;
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL
                | (hasSpotlight ? (targetBottomHalf ? Gravity.TOP : Gravity.BOTTOM) : Gravity.CENTER_VERTICAL);
        params.topMargin = (hasSpotlight && targetBottomHalf) ? (int) dp(88) : 0;
        params.bottomMargin = (hasSpotlight && !targetBottomHalf) ? (int) dp(120) : 0;
        card.setLayoutParams(params);

        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(180).start();
        invalidate();
    }

    private float dp(int value) {
        return value * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        if (hasSpotlight) {
            float radius = dp(16);
            canvas.drawRoundRect(spotlight, radius, radius, clearPaint);
        }
        canvas.restoreToCount(save);
    }
}

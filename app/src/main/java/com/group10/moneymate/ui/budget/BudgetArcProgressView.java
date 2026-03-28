package com.group10.moneymate.ui.budget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;

public class BudgetArcProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float progressFraction = 0f;
    private float strokeWidth;
    private float thumbRadius;
    private int progressColor;

    public BudgetArcProgressView(Context context) {
        this(context, null);
    }

    public BudgetArcProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BudgetArcProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        strokeWidth = dpToPx(14f);
        thumbRadius = dpToPx(11f);
        progressColor = ContextCompat.getColor(context, R.color.budget_safe_green);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.budget_track_gray));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setColor(progressColor);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(progressColor);
    }

    public void setProgressFraction(float progressFraction) {
        float safeProgress = Math.max(0f, Math.min(1f, progressFraction));
        if (this.progressFraction != safeProgress) {
            this.progressFraction = safeProgress;
            invalidate();
        }
    }

    public void setProgressColor(@ColorInt int progressColor) {
        this.progressColor = progressColor;
        progressPaint.setColor(progressColor);
        thumbPaint.setColor(progressColor);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) {
            width = (int) dpToPx(320f);
        }
        int desiredHeight = (int) (width / 2f + dpToPx(24f));
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolvedHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float horizontalInset = strokeWidth;
        float verticalInset = strokeWidth;
        arcBounds.set(horizontalInset,
                verticalInset,
                getWidth() - horizontalInset,
                getWidth() - horizontalInset);

        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint);
        if (progressFraction <= 0f) {
            return;
        }

        float sweepAngle = 180f * progressFraction;
        canvas.drawArc(arcBounds, 180f, sweepAngle, false, progressPaint);

        double radians = Math.toRadians(180f + sweepAngle);
        float centerX = arcBounds.centerX();
        float centerY = arcBounds.centerY();
        float radius = arcBounds.width() / 2f;
        float thumbX = (float) (centerX + radius * Math.cos(radians));
        float thumbY = (float) (centerY + radius * Math.sin(radians));

        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);
        thumbPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(thumbX, thumbY, thumbRadius - dpToPx(4f), thumbPaint);
        thumbPaint.setColor(progressColor);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}

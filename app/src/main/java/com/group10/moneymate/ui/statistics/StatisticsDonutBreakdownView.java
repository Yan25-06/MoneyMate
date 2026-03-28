package com.group10.moneymate.ui.statistics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StatisticsDonutBreakdownView extends View {

    private static final int MAX_VISIBLE_CALLOUTS = 4;
    private static final float ANGLE_DEVIATION_WEIGHT = 0.8f;
    private static final float RADIUS_DEVIATION_WEIGHT = 0.12f;

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final Rect percentBounds = new Rect();
    private final float[] tempPoint = new float[2];
    private final List<Segment> segments = new ArrayList<>();

    private double totalAmount;

    public StatisticsDonutBreakdownView(Context context) {
        super(context);
        init();
    }

    public StatisticsDonutBreakdownView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StatisticsDonutBreakdownView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        connectorPaint.setStyle(Paint.Style.STROKE);
        connectorPaint.setStrokeWidth(dp(1.8f));
        connectorPaint.setStrokeCap(Paint.Cap.ROUND);

        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(Color.WHITE);

        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(Color.WHITE);

        chipStrokePaint.setStyle(Paint.Style.STROKE);
        chipStrokePaint.setStrokeWidth(dp(1f));

        chipShadowPaint.setStyle(Paint.Style.FILL);
        chipShadowPaint.setColor(0x14000000);

        percentPaint.setColor(ContextCompat.getColor(getContext(), R.color.statistics_text_muted));
        percentPaint.setTextSize(sp(11f));
        percentPaint.setFakeBoldText(true);
        percentPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(@Nullable List<Segment> items, double totalAmount) {
        segments.clear();
        if (items != null) {
            segments.addAll(items);
        }
        this.totalAmount = totalAmount;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (segments.isEmpty() || totalAmount <= 0d) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height * 0.56f;
        float outerRadius = Math.min(width * 0.215f, height * 0.19f);
        float ringThickness = outerRadius * 0.42f;

        arcBounds.set(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius, centerY + outerRadius);
        drawSlices(canvas, centerX, centerY, outerRadius, ringThickness);

        List<CalloutLayout> callouts = buildCallouts(centerX, centerY, outerRadius, width, height);
        for (CalloutLayout callout : callouts) {
            drawConnector(canvas, centerX, centerY, outerRadius, callout);
            drawChip(canvas, callout);
            drawPercent(canvas, callout);
        }
    }

    private void drawSlices(@NonNull Canvas canvas,
                            float centerX,
                            float centerY,
                            float outerRadius,
                            float ringThickness) {
        float startAngle = -90f;
        float sliceGap = segments.size() > 1 ? 2f : 0f;
        for (Segment segment : segments) {
            float sweepAngle = (float) ((segment.amount / totalAmount) * 360f);
            slicePaint.setColor(segment.color);
            canvas.drawArc(arcBounds, startAngle, Math.max(0f, sweepAngle - sliceGap), true, slicePaint);
            startAngle += sweepAngle;
        }
        canvas.drawCircle(centerX, centerY, outerRadius - ringThickness, holePaint);
    }

    @NonNull
    private List<CalloutLayout> buildCallouts(float centerX,
                                              float centerY,
                                              float outerRadius,
                                              float width,
                                              float height) {
        List<SegmentAngle> visible = collectVisibleSegments();
        Collections.sort(visible, Comparator.comparingDouble(item -> normalizeAngle(item.midAngle)));

        List<CalloutLayout> layouts = new ArrayList<>();
        float baseRadius = outerRadius + dp(34f);
        float minGap = dp(32f);
        for (SegmentAngle item : visible) {
            CalloutLayout layout = createBaseLayout(
                    item.segment,
                    item.midAngle,
                    String.format(Locale.getDefault(), "%.0f%%", (item.amount / totalAmount) * 100d),
                    baseRadius,
                    centerX,
                    centerY,
                    outerRadius,
                    width,
                    height
            );
            resolvePlacement(layout, layouts, centerX, centerY, outerRadius, width, height, minGap);
            layouts.add(layout);
        }
        return layouts;
    }

    @NonNull
    private List<SegmentAngle> collectVisibleSegments() {
        List<SegmentAngle> all = new ArrayList<>();
        float startAngle = -90f;
        for (Segment segment : segments) {
            float sweepAngle = (float) ((segment.amount / totalAmount) * 360f);
            all.add(new SegmentAngle(segment, startAngle + (sweepAngle / 2f), segment.amount));
            startAngle += sweepAngle;
        }
        Collections.sort(all, Comparator.comparingDouble((SegmentAngle item) -> item.amount).reversed());
        if (all.size() > MAX_VISIBLE_CALLOUTS) {
            return new ArrayList<>(all.subList(0, MAX_VISIBLE_CALLOUTS));
        }
        return all;
    }

    @NonNull
    private CalloutLayout createBaseLayout(@NonNull Segment segment,
                                           float angle,
                                           @NonNull String percentText,
                                           float radialDistance,
                                           float centerX,
                                           float centerY,
                                           float outerRadius,
                                           float width,
                                           float height) {
        CalloutLayout layout = new CalloutLayout(segment, angle, percentText, radialDistance);
        recalculateLayout(layout, centerX, centerY, outerRadius, width, height);
        return layout;
    }

    private void resolvePlacement(@NonNull CalloutLayout current,
                                  @NonNull List<CalloutLayout> placedLayouts,
                                  float centerX,
                                  float centerY,
                                  float outerRadius,
                                  float width,
                                  float height,
                                  float minGap) {
        float baseAngle = current.sourceAngle;
        float baseRadius = current.radialDistance;
        float[] angleOffsets = new float[]{0f, -12f, 12f, -24f, 24f, -36f, 36f};
        float[] radiusAdjustments = new float[]{0f, -8f, -16f, -24f};

        CalloutLayout bestFallback = null;
        float bestScore = Float.MAX_VALUE;

        for (float radiusAdjustment : radiusAdjustments) {
            float targetRadius = Math.max(dp(18f), baseRadius + radiusAdjustment);
            for (float angleOffset : angleOffsets) {
                current.displayAngle = baseAngle + angleOffset;
                current.radialDistance = targetRadius;
                recalculateLayout(current, centerX, centerY, outerRadius, width, height);

                float score = placementPenalty(current, placedLayouts, centerX, centerY, outerRadius, width, height, minGap);
                if (score < bestScore) {
                    bestScore = score;
                    bestFallback = snapshot(current);
                }
                if (score <= 0.01f) {
                    return;
                }
            }
        }

        if (bestFallback != null) {
            applySnapshot(current, bestFallback);
        }
    }

    private boolean isInsideBounds(@NonNull CalloutLayout layout,
                                   float centerX,
                                   float centerY,
                                   float outerRadius,
                                   float width,
                                   float height) {
        float chipRadius = dp(12f);
        if (layout.iconCenterX - chipRadius < dp(4f) || layout.iconCenterX + chipRadius > width - dp(4f)) {
            return false;
        }
        if (layout.iconCenterY - chipRadius < dp(4f) || layout.iconCenterY + chipRadius > height - dp(4f)) {
            return false;
        }
        float donutSafetyRadius = outerRadius + dp(6f);
        float chipMinDistance = donutSafetyRadius + chipRadius;
        if (distanceSquared(layout.iconCenterX, layout.iconCenterY, centerX, centerY) < (chipMinDistance * chipMinDistance)) {
            return false;
        }
        percentPaint.getTextBounds(layout.percentText, 0, layout.percentText.length(), percentBounds);
        float halfTextWidth = percentBounds.width() / 2f;
        float halfTextHeight = percentBounds.height() / 2f;
        if (layout.percentCenterX - halfTextWidth < dp(4f)
                || layout.percentCenterX + halfTextWidth > width - dp(4f)
                || layout.percentCenterY - halfTextHeight < dp(8f)
                || layout.percentCenterY + halfTextHeight > height - dp(8f)) {
            return false;
        }

        RectF percentRect = new RectF(
                layout.percentCenterX - halfTextWidth,
                layout.percentCenterY - halfTextHeight,
                layout.percentCenterX + halfTextWidth,
                layout.percentCenterY + halfTextHeight
        );
        return !rectIntersectsCircle(percentRect, centerX, centerY, donutSafetyRadius);
    }

    private float placementPenalty(@NonNull CalloutLayout current,
                                   @NonNull List<CalloutLayout> placedLayouts,
                                   float centerX,
                                   float centerY,
                                   float outerRadius,
                                   float width,
                                   float height,
                                   float minGap) {
        float penalty = 0f;
        if (!isInsideBounds(current, centerX, centerY, outerRadius, width, height)) {
            penalty += 1000f;
        }
        for (CalloutLayout placed : placedLayouts) {
            float iconDistance = distance(current.iconCenterX, current.iconCenterY, placed.iconCenterX, placed.iconCenterY);
            if (iconDistance < minGap) {
                penalty += (minGap - iconDistance) * 10f;
            }
            if (percentBoundsIntersect(current, placed)) {
                penalty += 500f;
            }
        }
        penalty += Math.abs(current.displayAngle - current.sourceAngle) * ANGLE_DEVIATION_WEIGHT;
        penalty += Math.abs(current.radialDistance - current.baseRadiusReference) * RADIUS_DEVIATION_WEIGHT;
        return penalty;
    }

    private boolean percentBoundsIntersect(@NonNull CalloutLayout first, @NonNull CalloutLayout second) {
        percentPaint.getTextBounds(first.percentText, 0, first.percentText.length(), percentBounds);
        float firstHalfWidth = percentBounds.width() / 2f;
        float firstHalfHeight = percentBounds.height() / 2f;
        RectF firstRect = new RectF(
                first.percentCenterX - firstHalfWidth,
                first.percentCenterY - firstHalfHeight,
                first.percentCenterX + firstHalfWidth,
                first.percentCenterY + firstHalfHeight
        );

        percentPaint.getTextBounds(second.percentText, 0, second.percentText.length(), percentBounds);
        float secondHalfWidth = percentBounds.width() / 2f;
        float secondHalfHeight = percentBounds.height() / 2f;
        RectF secondRect = new RectF(
                second.percentCenterX - secondHalfWidth,
                second.percentCenterY - secondHalfHeight,
                second.percentCenterX + secondHalfWidth,
                second.percentCenterY + secondHalfHeight
        );
        return RectF.intersects(firstRect, secondRect);
    }

    @NonNull
    private CalloutLayout snapshot(@NonNull CalloutLayout source) {
        CalloutLayout copy = new CalloutLayout(source.segment, source.sourceAngle, source.percentText, source.baseRadiusReference);
        copy.displayAngle = source.displayAngle;
        copy.radialDistance = source.radialDistance;
        copy.connectorStartRadius = source.connectorStartRadius;
        copy.iconCenterX = source.iconCenterX;
        copy.iconCenterY = source.iconCenterY;
        copy.connectorEndX = source.connectorEndX;
        copy.connectorEndY = source.connectorEndY;
        copy.percentCenterX = source.percentCenterX;
        copy.percentCenterY = source.percentCenterY;
        return copy;
    }

    private void applySnapshot(@NonNull CalloutLayout target, @NonNull CalloutLayout snapshot) {
        target.displayAngle = snapshot.displayAngle;
        target.radialDistance = snapshot.radialDistance;
        target.connectorStartRadius = snapshot.connectorStartRadius;
        target.iconCenterX = snapshot.iconCenterX;
        target.iconCenterY = snapshot.iconCenterY;
        target.connectorEndX = snapshot.connectorEndX;
        target.connectorEndY = snapshot.connectorEndY;
        target.percentCenterX = snapshot.percentCenterX;
        target.percentCenterY = snapshot.percentCenterY;
    }

    private void recalculateLayout(@NonNull CalloutLayout layout,
                                   float centerX,
                                   float centerY,
                                   float outerRadius,
                                   float width,
                                   float height) {
        float chipRadius = dp(12f);
        pointOnCircle(centerX, centerY, layout.radialDistance, layout.displayAngle, tempPoint);
        layout.iconCenterX = tempPoint[0];
        layout.iconCenterY = tempPoint[1];

        float directionX = layout.iconCenterX - centerX;
        float directionY = layout.iconCenterY - centerY;
        float vectorSize = (float) Math.sqrt((directionX * directionX) + (directionY * directionY));
        float unitX = vectorSize > 0f ? directionX / vectorSize : 0f;
        float unitY = vectorSize > 0f ? directionY / vectorSize : -1f;

        layout.connectorStartRadius = layout.radialDistance - chipRadius;
        layout.connectorEndX = layout.iconCenterX - (unitX * chipRadius);
        layout.connectorEndY = layout.iconCenterY - (unitY * chipRadius);

        percentPaint.getTextBounds(layout.percentText, 0, layout.percentText.length(), percentBounds);
        float halfTextWidth = percentBounds.width() / 2f;
        float percentOffset = chipRadius + dp(10f) + halfTextWidth;
        float preferredX = layout.iconCenterX + (unitX * percentOffset);
        float preferredY = layout.iconCenterY + (unitY * percentOffset);
        applyPercentFallback(
                layout,
                preferredX,
                preferredY,
                unitX,
                unitY,
                centerX,
                centerY,
                outerRadius,
                width,
                height,
                chipRadius
        );
    }

    private void applyPercentFallback(@NonNull CalloutLayout layout,
                                      float preferredX,
                                      float preferredY,
                                      float unitX,
                                      float unitY,
                                      float donutCenterX,
                                      float donutCenterY,
                                      float outerRadius,
                                      float width,
                                      float height,
                                      float chipRadius) {
        percentPaint.getTextBounds(layout.percentText, 0, layout.percentText.length(), percentBounds);
        float halfTextWidth = percentBounds.width() / 2f;
        float halfTextHeight = percentBounds.height() / 2f;
        float sideGap = chipRadius + dp(10f) + halfTextWidth;
        float verticalGap = chipRadius + dp(10f) + halfTextHeight;

        float[][] candidates = new float[][]{
                {preferredX, preferredY},
                {layout.iconCenterX, layout.iconCenterY - verticalGap},
                {layout.iconCenterX, layout.iconCenterY + verticalGap},
                {layout.iconCenterX - sideGap, layout.iconCenterY},
                {layout.iconCenterX + sideGap, layout.iconCenterY},
                {layout.iconCenterX - (unitX * sideGap), layout.iconCenterY - (unitY * sideGap)}
        };

        for (float[] candidate : candidates) {
            if (isPercentInside(
                    candidate[0],
                    candidate[1],
                    halfTextWidth,
                    halfTextHeight,
                    donutCenterX,
                    donutCenterY,
                    outerRadius,
                    width,
                    height
            )) {
                layout.percentCenterX = candidate[0];
                layout.percentCenterY = candidate[1];
                return;
            }
        }

        layout.percentCenterX = preferredX;
        layout.percentCenterY = preferredY;
    }

    private boolean isPercentInside(float centerX,
                                    float centerY,
                                    float halfWidth,
                                    float halfHeight,
                                    float donutCenterX,
                                    float donutCenterY,
                                    float outerRadius,
                                    float width,
                                    float height) {
        if (centerX - halfWidth < dp(4f)
                || centerX + halfWidth > width - dp(4f)
                || centerY - halfHeight < dp(8f)
                || centerY + halfHeight > height - dp(8f)) {
            return false;
        }
        RectF percentRect = new RectF(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight
        );
        return !rectIntersectsCircle(percentRect, donutCenterX, donutCenterY, outerRadius + dp(6f));
    }

    private void drawConnector(@NonNull Canvas canvas,
                               float centerX,
                               float centerY,
                               float outerRadius,
                               @NonNull CalloutLayout layout) {
        connectorPaint.setColor(layout.segment.color);
        pointOnCircle(centerX, centerY, outerRadius + dp(2f), layout.displayAngle, tempPoint);
        canvas.drawLine(tempPoint[0], tempPoint[1], layout.connectorEndX, layout.connectorEndY, connectorPaint);
    }

    private void drawChip(@NonNull Canvas canvas, @NonNull CalloutLayout layout) {
        float radius = dp(12f);
        canvas.drawCircle(layout.iconCenterX + dp(2f), layout.iconCenterY + dp(3f), radius, chipShadowPaint);
        canvas.drawCircle(layout.iconCenterX, layout.iconCenterY, radius, chipPaint);
        chipStrokePaint.setColor(adjustAlpha(layout.segment.color, 0.20f));
        canvas.drawCircle(layout.iconCenterX, layout.iconCenterY, radius - dp(0.5f), chipStrokePaint);

        Drawable drawable = ContextCompat.getDrawable(getContext(), layout.segment.iconResId);
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_category_other);
        }
        if (drawable == null) {
            return;
        }
        drawable = drawable.mutate();
        drawable.setTint(layout.segment.color);
        int iconHalf = (int) dp(6.5f);
        drawable.setBounds(
                (int) (layout.iconCenterX - iconHalf),
                (int) (layout.iconCenterY - iconHalf),
                (int) (layout.iconCenterX + iconHalf),
                (int) (layout.iconCenterY + iconHalf)
        );
        drawable.draw(canvas);
    }

    private void drawPercent(@NonNull Canvas canvas, @NonNull CalloutLayout layout) {
        float baseline = layout.percentCenterY - ((percentPaint.ascent() + percentPaint.descent()) / 2f);
        canvas.drawText(layout.percentText, layout.percentCenterX, baseline, percentPaint);
    }

    private void pointOnCircle(float centerX, float centerY, float radius, float angleDegrees, @NonNull float[] outPoint) {
        double radians = Math.toRadians(angleDegrees);
        outPoint[0] = centerX + ((float) Math.cos(radians) * radius);
        outPoint[1] = centerY + ((float) Math.sin(radians) * radius);
    }

    private float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        if (normalized < 0f) {
            normalized += 360f;
        }
        return normalized;
    }

    private float distance(float startX, float startY, float endX, float endY) {
        float dx = endX - startX;
        float dy = endY - startY;
        return (float) Math.sqrt((dx * dx) + (dy * dy));
    }

    private float distanceSquared(float startX, float startY, float endX, float endY) {
        float dx = endX - startX;
        float dy = endY - startY;
        return (dx * dx) + (dy * dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean rectIntersectsCircle(@NonNull RectF rect,
                                         float circleCenterX,
                                         float circleCenterY,
                                         float radius) {
        float nearestX = clamp(circleCenterX, rect.left, rect.right);
        float nearestY = clamp(circleCenterY, rect.top, rect.bottom);
        return distanceSquared(nearestX, nearestY, circleCenterX, circleCenterY) < (radius * radius);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @ColorInt
    private int adjustAlpha(@ColorInt int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static final class Segment {
        @DrawableRes
        public final int iconResId;
        @ColorInt
        public final int color;
        public final double amount;

        public Segment(@DrawableRes int iconResId, @ColorInt int color, double amount) {
            this.iconResId = iconResId;
            this.color = color;
            this.amount = amount;
        }
    }

    private static final class SegmentAngle {
        private final Segment segment;
        private final float midAngle;
        private final double amount;

        private SegmentAngle(@NonNull Segment segment, float midAngle, double amount) {
            this.segment = segment;
            this.midAngle = midAngle;
            this.amount = amount;
        }
    }

    private static final class CalloutLayout {
        private final Segment segment;
        private final float sourceAngle;
        private final String percentText;
        private final float baseRadiusReference;
        private float displayAngle;
        private float radialDistance;
        private float connectorStartRadius;
        private float iconCenterX;
        private float iconCenterY;
        private float connectorEndX;
        private float connectorEndY;
        private float percentCenterX;
        private float percentCenterY;

        private CalloutLayout(@NonNull Segment segment,
                              float sourceAngle,
                              @NonNull String percentText,
                              float radialDistance) {
            this.segment = segment;
            this.sourceAngle = sourceAngle;
            this.percentText = percentText;
            this.baseRadiusReference = radialDistance;
            this.displayAngle = sourceAngle;
            this.radialDistance = radialDistance;
        }
    }
}
